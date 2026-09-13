package com.synpharm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.client.FastApiClient;
import com.synpharm.dto.request.PredictRequest;
import com.synpharm.dto.response.BatchItemPageResponse;
import com.synpharm.dto.response.BatchItemResponse;
import com.synpharm.dto.response.BatchPredictionResponse;
import com.synpharm.dto.response.BatchStatusResponse;
import com.synpharm.dto.response.BatchUploadResponse;
import com.synpharm.dto.response.PredictResultResponse;
import com.synpharm.exception.BusinessException;
import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import com.synpharm.model.entity.BatchTask;
import com.synpharm.model.entity.BatchTaskItem;
import com.synpharm.model.entity.PredictResult;
import com.synpharm.model.entity.PredictTask;
import com.synpharm.mq.BatchTaskProducer;
import com.synpharm.pipeline.resolve.PredictionInputResolver;
import com.synpharm.pipeline.resolve.ResolvedPredictionInput;
import com.synpharm.repository.mapper.BatchTaskItemMapper;
import com.synpharm.repository.mapper.BatchTaskMapper;
import com.synpharm.repository.mapper.PredictResultMapper;
import com.synpharm.repository.mapper.PredictTaskMapper;
import com.synpharm.service.BatchProcessService;
import com.synpharm.utils.CsvUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessServiceImpl implements BatchProcessService {

    private final BatchTaskMapper batchTaskMapper;
    private final BatchTaskItemMapper batchTaskItemMapper;
    private final PredictTaskMapper predictTaskMapper;
    private final PredictResultMapper predictResultMapper;
    private final FastApiClient fastApiClient;
    private final PredictionInputResolver inputResolver;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BatchTaskProducer batchTaskProducer;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.result-dir:./results}")
    private String resultDir;

    private static final int CHUNK_SIZE = 50;
    private static final int PROGRESS_UPDATE_INTERVAL = 5;
    private static final String PROGRESS_KEY = "batch:progress:";
    private static final int PROGRESS_EXPIRE_HOURS = 24;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchUploadResponse uploadBatch(MultipartFile file, String algoType, Long userId) {
        String batchId = UUID.randomUUID().toString();
        String fileName = batchId + "_" + file.getOriginalFilename();
        String filePath = uploadDir + "/" + fileName;

        try {
            File uploadFile = new File(filePath);
            uploadFile.getParentFile().mkdirs();
            Files.copy(file.getInputStream(), uploadFile.toPath());

            int totalCount = CsvUtils.countRows(filePath);

            BatchTask task = new BatchTask();
            task.setBatchId(batchId);
            task.setUserId(userId);
            task.setFilePath(filePath);
            task.setTotalCount(totalCount);
            task.setSuccessCount(0);
            task.setFailCount(0);
            task.setProgress(BigDecimal.ZERO);
            task.setStatus(0);
            task.setAlgoType(algoType);
            batchTaskMapper.insert(task);

            // 批量闭环（修复方案 5.6）：为每一行创建明细记录
            createItemsFromFile(batchId, filePath);

            saveProgress(batchId, task);

            // 事务提交后再发送消息，避免消费者在事务未提交时读到不到记录（batch_task 为权威）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    batchTaskProducer.sendBatchTask(batchId, algoType);
                }
            });

            return BatchUploadResponse.builder()
                    .batchId(batchId)
                    .totalCount(totalCount)
                    .status("PENDING")
                    .build();

        } catch (Exception e) {
            log.error("批量上传失败", e);
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    public void processBatch(String batchId, String algoType) {
        log.info("开始处理批量任务: {}", batchId);

        // 以 DB 为权威（Redis 仅缓存），消息消费时从 DB 读取
        BatchTask task = batchTaskMapper.selectByBatchId(batchId);
        if (task == null) {
            log.error("批量任务不存在: {}", batchId);
            return;
        }

        // 幂等：已在处理中/已完成则跳过（防重复消费/重复投递）
        if (task.getStatus() != null && (task.getStatus() == 1 || task.getStatus() == 2)) {
            log.info("批量任务已在处理或已完成, 跳过: batchId={}, status={}", batchId, task.getStatus());
            return;
        }

        if (algoType != null && !algoType.isBlank()) {
            task.setAlgoType(algoType);
        }

        task.setStatus(1);
        batchTaskMapper.updateById(task);
        saveProgress(batchId, task);

        // 批量闭环：逐行处理明细（行级失败不阻断批次）
        List<BatchTaskItem> items = batchTaskItemMapper.selectList(
                new LambdaQueryWrapper<BatchTaskItem>()
                        .eq(BatchTaskItem::getBatchId, batchId)
                        .orderByAsc(BatchTaskItem::getRowNumber)
        );

        // 兼容旧批次（改造前上传、明细表为空）：处理时从 CSV 补建明细
        if (items.isEmpty()) {
            items = createItemsFromFile(batchId, task.getFilePath());
        }

        // 已成功(2)的行跳过；0 待处理与 3 上次失败的行参与本次处理
        List<BatchTaskItem> processable = items.stream()
                .filter(item -> item.getStatus() == null || item.getStatus() != 2)
                .collect(Collectors.toList());

        List<Map<String, Object>> allResults = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        int chunkIndex = 0;

        try {
            for (int from = 0; from < processable.size(); from += CHUNK_SIZE) {
                int to = Math.min(from + CHUNK_SIZE, processable.size());
                List<BatchTaskItem> chunk = processable.subList(from, to);

                // 1. 行级输入解析（失败只标记该行，不阻断批次）
                List<RowInput> resolvedRows = new ArrayList<>();
                for (BatchTaskItem item : chunk) {
                    try {
                        ResolvedPredictionInput resolved =
                                inputResolver.resolve(task.getAlgoType(), "smiles", item.getInputValue());
                        resolvedRows.add(new RowInput(item, resolved));
                    } catch (PredictionException e) {
                        markItemFailed(item, e.getErrorCode(), e.getMessage());
                        failCount++;
                    }
                }

                // 2. 按解析成功的行批量调用算法引擎
                if (!resolvedRows.isEmpty()) {
                    List<PredictRequest> requests = resolvedRows.stream()
                            .map(row -> toPredictRequest(row.resolved, task.getAlgoType()))
                            .collect(Collectors.toList());

                    BatchPredictionResponse batchResponse;
                    try {
                        batchResponse = fastApiClient.predictBatch(requests, task.getAlgoType());
                    } catch (Exception e) {
                        // 批次级失败：本块全部标记失败，批次置 FAIL 后继续抛出（消费者 nack 进死信）
                        log.error("批量预测批次级失败: batchId={}, 本块行数={}, error={}",
                                batchId, resolvedRows.size(), e.getMessage());
                        for (RowInput row : resolvedRows) {
                            markItemFailed(row.item, PredictionErrorCode.FASTAPI_UNAVAILABLE,
                                    "算法引擎调用失败: " + e.getMessage());
                            failCount++;
                        }
                        failBatch(task, batchId, "算法引擎调用失败: " + e.getMessage());
                        throw new RuntimeException("算法引擎调用失败: " + e.getMessage(), e);
                    }

                    // 3. 逐行回写（FastAPI 批量接口按序返回，每行必有结果，失败行含 error 字段）
                    List<Map<String, Object>> results = batchResponse == null
                            || batchResponse.getResults() == null ? List.of() : batchResponse.getResults();
                    for (int i = 0; i < resolvedRows.size(); i++) {
                        RowInput row = resolvedRows.get(i);
                        Map<String, Object> resultMap = i < results.size() ? results.get(i) : null;
                        if (resultMap == null || resultMap.containsKey("error")) {
                            String errorMessage = resultMap != null
                                    ? String.valueOf(resultMap.get("error"))
                                    : "算法引擎未返回该行结果";
                            markItemFailed(row.item, PredictionErrorCode.MODEL_UNAVAILABLE, errorMessage);
                            failCount++;
                            continue;
                        }
                        PredictResultResponse response = convertResult(resultMap, task.getAlgoType(), row.resolved);
                        saveRecord(task, row.item, response);
                        successCount++;
                        allResults.add(toResultMap(response));
                    }
                }

                // 4. 进度回写（按块间隔落库，Redis 每块更新）
                chunkIndex++;
                int processedCount = successCount + failCount;
                task.setSuccessCount(successCount);
                task.setFailCount(failCount);
                task.setProgress(percent(processedCount, items.size()));
                if (chunkIndex % PROGRESS_UPDATE_INTERVAL == 0) {
                    batchTaskMapper.updateById(task);
                }
                saveProgress(batchId, task);
            }

            // 5. 成功收尾：从 DB 重算统计（含历史已成功行，支持重投递场景）、
            //    结果 CSV（保留既有下载功能）、状态落库
            String resultPath = resultDir + "/" + batchId + "_result.csv";
            CsvUtils.writeResultFile(resultPath, task.getAlgoType(), allResults);

            task.setSuccessCount(countItems(batchId, 2));
            task.setFailCount(countItems(batchId, 3));
            task.setStatus(2);
            task.setResultUrl("/api/batch/download/" + batchId);
            task.setProgress(BigDecimal.valueOf(100));
            batchTaskMapper.updateById(task);

            redisTemplate.delete(PROGRESS_KEY + batchId);

            log.info("批量任务处理完成: batchId={}, 成功={}, 失败={}", batchId, successCount, failCount);

        } catch (Exception e) {
            // 批次级失败：状态落库后继续抛出，消费者 basicNack 进死信队列（修复方案 5.6）
            log.error("批量任务处理失败: batchId={}, error={}", batchId, e.getMessage());
            failBatch(task, batchId, e.getMessage());
            throw new RuntimeException("批量任务处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public BatchStatusResponse getBatchStatus(String batchId, Long userId) {
        BatchTask task = batchTaskMapper.selectByBatchId(batchId);
        if (task == null) {
            throw new BusinessException("批次任务不存在");
        }

        // 归属校验：仅本人可查看（修复 IDOR）
        if (userId != null && !userId.equals(task.getUserId())) {
            throw new BusinessException("无权访问该批次任务");
        }

        String statusText = switch (task.getStatus()) {
            case 0 -> "PENDING";
            case 1 -> "PROCESSING";
            case 2 -> "SUCCESS";
            case 3 -> "FAIL";
            default -> "UNKNOWN";
        };

        return BatchStatusResponse.builder()
                .batchId(task.getBatchId())
                .algoType(task.getAlgoType())
                .totalCount(task.getTotalCount())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .progress(task.getProgress())
                .status(statusText)
                .resultUrl(task.getResultUrl())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    @Override
    public BatchItemPageResponse getBatchItems(String batchId, Long userId, Integer page, Integer pageSize, Integer status) {
        BatchTask task = batchTaskMapper.selectByBatchId(batchId);
        if (task == null) {
            throw new BusinessException("批次任务不存在");
        }
        if (userId != null && !userId.equals(task.getUserId())) {
            throw new BusinessException("无权访问该批次任务");
        }

        // 内存分页（项目未配置 MyBatis-Plus 分页插件，与 ResultServiceImpl 保持一致）
        List<BatchTaskItem> all = batchTaskItemMapper.selectList(new LambdaQueryWrapper<BatchTaskItem>()
                .eq(BatchTaskItem::getBatchId, batchId)
                .eq(status != null, BatchTaskItem::getStatus, status)
                .orderByAsc(BatchTaskItem::getRowNumber)
        );

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int total = all.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(total, from + safeSize);
        List<BatchItemResponse> list = from < total
                ? all.subList(from, to).stream().map(this::toItemResponse).collect(Collectors.toList())
                : List.of();

        return BatchItemPageResponse.builder()
                .total((long) total)
                .page((long) safePage)
                .pageSize((long) safeSize)
                .list(list)
                .build();
    }

    @Override
    public ResponseEntity<Resource> downloadBatch(String batchId, Long userId) {
        BatchTask task = batchTaskMapper.selectByBatchId(batchId);
        if (task == null) {
            throw new BusinessException("批次任务不存在");
        }

        // 归属校验：仅本人可下载（修复 IDOR）
        if (userId != null && !userId.equals(task.getUserId())) {
            throw new BusinessException("无权访问该批次任务");
        }

        String resultPath = resultDir + "/" + batchId + "_result.csv";
        File file = new File(resultPath);

        if (!file.exists()) {
            throw new BusinessException("结果文件不存在");
        }

        Resource resource = new FileSystemResource(file);
        String filename = batchId + "_result.csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ================= 批量闭环辅助方法 =================

    /**
     * 为 CSV 的每一行创建明细记录（上传时建明细；旧批次处理时补建）。
     */
    private List<BatchTaskItem> createItemsFromFile(String batchId, String filePath) {
        List<BatchTaskItem> items = new ArrayList<>();
        try {
            List<String> lines = CsvUtils.readLines(filePath);
            int rowNumber = 0;
            for (String line : lines) {
                BatchTaskItem item = new BatchTaskItem();
                item.setBatchId(batchId);
                item.setRowNumber(++rowNumber);
                item.setInputValue(line);
                item.setStatus(0);
                batchTaskItemMapper.insert(item);
                items.add(item);
            }
            log.info("批量明细创建完成: batchId={}, 行数={}", batchId, items.size());
        } catch (IOException e) {
            log.error("读取批量CSV失败: batchId={}, error={}", batchId, e.getMessage());
            throw new BusinessException("读取批量CSV文件失败");
        }
        return items;
    }

    /**
     * 标记单行失败：状态置 3 + 字符串错误码与错误信息。
     */
    private void markItemFailed(BatchTaskItem item, PredictionErrorCode errorCode, String message) {
        item.setStatus(3);
        item.setErrorCode(errorCode.name());
        item.setErrorMessage(message == null || message.isBlank() ? errorCode.getDefaultMessage() : message);
        batchTaskItemMapper.updateById(item);
    }

    /**
     * 批次置失败（幂等：已失败则不重复写）。
     */
    private void failBatch(BatchTask task, String batchId, String errorMessage) {
        if (task.getStatus() != null && task.getStatus() == 3) {
            return;
        }
        task.setStatus(3);
        task.setErrorMsg(errorMessage);
        batchTaskMapper.updateById(task);
        saveProgress(batchId, task);
    }

    /**
     * 成功行落库：创建隐式任务 + 预测结果，并回写明细 result_id（修复方案 5.6）。
     */
    private void saveRecord(BatchTask task, BatchTaskItem item, PredictResultResponse response) {
        try {
            PredictTask predictTask = new PredictTask();
            predictTask.setTaskNo(generateNo("T"));
            predictTask.setUserId(task.getUserId());
            predictTask.setPredictType(task.getAlgoType() == null ? null : task.getAlgoType().toLowerCase());
            predictTask.setInputType("smiles");
            predictTask.setInputValue(item.getInputValue());
            predictTask.setStatus("completed");
            predictTask.setProgress(100);
            predictTask.setStartedAt(LocalDateTime.now());
            predictTask.setCompletedAt(LocalDateTime.now());
            predictTaskMapper.insert(predictTask);

            PredictResult result = new PredictResult();
            result.setResultNo(generateNo("R"));
            result.setTaskId(predictTask.getId());
            result.setUserId(task.getUserId());
            result.setTargetId(response.getTargetId());
            result.setTargetName(response.getTargetName());
            result.setLigandSmiles(response.getLigandSmiles());
            result.setBindingAffinity(response.getBindingAffinity());
            result.setConfidenceScore(response.getConfidenceScore());
            result.setConfidenceLevel(response.getConfidenceLevel());
            result.setInteractions(writeJson(response.getInteractions()));
            result.setPredictionData(writeJson(response));
            result.setDatasetSource("batch-predict");
            predictResultMapper.insert(result);

            item.setStatus(2);
            item.setResultId(result.getId());
            item.setErrorCode(null);
            item.setErrorMessage(null);
            batchTaskItemMapper.updateById(item);

            response.setId(result.getId());
            response.setCreatedAt(result.getCreatedAt());
        } catch (Exception e) {
            log.error("批量行落库失败: itemId={}, error={}", item.getId(), e.getMessage());
            markItemFailed(item, PredictionErrorCode.MODEL_UNAVAILABLE, "结果落库失败: " + e.getMessage());
        }
    }

    /**
     * 将 FastAPI 批量返回的平铺字段（snake_case）转换为统一响应（与各 AlgoExecutor 约定一致）。
     */
    private PredictResultResponse convertResult(Map<String, Object> result, String algoType, ResolvedPredictionInput resolved) {
        String ligand = null;
        if ("DTI".equalsIgnoreCase(algoType)) {
            ligand = resolved.getLigandSmiles();
        } else if ("DDI".equalsIgnoreCase(algoType)) {
            ligand = resolved.getDrugA();
        }

        return PredictResultResponse.builder()
                .algoType(algoType)
                .targetId(toStr(result.get("target_id")))
                .targetName(toStr(result.get("target_name")))
                .ligandSmiles(ligand)
                .bindingAffinity(toDouble(result.get("binding_affinity")))
                .confidenceScore(toDouble(result.get("confidence_score")))
                .confidenceLevel(toStr(result.get("confidence_level")))
                .interactions(parseInteractions(result.get("interactions")))
                .datasetInfo(PredictResultResponse.DatasetInfo.builder()
                        .name(algoType + "预测结果")
                        .size(0)
                        .description("由 FastAPI 算法引擎计算")
                        .source("fastapi")
                        .build())
                .build();
    }

    private PredictRequest toPredictRequest(ResolvedPredictionInput resolved, String algoType) {
        return switch (algoType == null ? "" : algoType.toUpperCase()) {
            case "DTI" -> PredictRequest.forDTI(resolved.getLigandSmiles(), resolved.getTargetSequence());
            case "PPI" -> PredictRequest.forPPI(resolved.getProteinA(), resolved.getProteinB());
            case "DDI" -> PredictRequest.forDDI(resolved.getDrugA(), resolved.getDrugB());
            default -> throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED, "未知算法类型: " + algoType);
        };
    }

    /** 兼容既有结果 CSV 格式（下载功能不变） */
    private Map<String, Object> toResultMap(PredictResultResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("algoType", response.getAlgoType());
        result.put("targetId", response.getTargetId());
        result.put("targetName", response.getTargetName());
        result.put("bindingAffinity", response.getBindingAffinity());
        result.put("confidenceScore", response.getConfidenceScore());
        result.put("confidenceLevel", response.getConfidenceLevel());
        return result;
    }

    private BatchItemResponse toItemResponse(BatchTaskItem item) {
        String statusText = switch (item.getStatus() == null ? 0 : item.getStatus()) {
            case 0 -> "PENDING";
            case 1 -> "PROCESSING";
            case 2 -> "SUCCESS";
            case 3 -> "FAIL";
            default -> "UNKNOWN";
        };
        return BatchItemResponse.builder()
                .id(item.getId())
                .batchId(item.getBatchId())
                .rowNumber(item.getRowNumber())
                .inputValue(item.getInputValue())
                .status(item.getStatus())
                .statusText(statusText)
                .resultId(item.getResultId())
                .errorCode(item.getErrorCode())
                .errorMessage(item.getErrorMessage())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private BigDecimal percent(int processed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(100.0 * processed / total).setScale(2, RoundingMode.HALF_UP);
    }

    /** 按状态统计明细行数（从 DB 重算，保证重投递场景统计准确） */
    private int countItems(String batchId, int status) {
        Long count = batchTaskItemMapper.selectCount(new LambdaQueryWrapper<BatchTaskItem>()
                .eq(BatchTaskItem::getBatchId, batchId)
                .eq(BatchTaskItem::getStatus, status));
        return count == null ? 0 : count.intValue();
    }

    private String generateNo(String prefix) {
        return prefix + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String writeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str && !str.isEmpty()) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                // 忽略无法解析的数值，返回 null
            }
        }
        return null;
    }

    private List<PredictResultResponse.InteractionInfo> parseInteractions(Object value) {
        List<PredictResultResponse.InteractionInfo> interactions = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    PredictResultResponse.InteractionInfo info = PredictResultResponse.InteractionInfo.builder()
                            .type(toStr(map.get("type")))
                            .residueName(toStr(map.get("residue")))
                            .distance(toDouble(map.get("distance")))
                            .build();
                    interactions.add(info);
                }
            }
        }
        return interactions;
    }

    private void saveProgress(String batchId, BatchTask task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForValue().set(PROGRESS_KEY + batchId, json, PROGRESS_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("保存批量任务进度失败: {}", batchId, e);
        }
    }

    /** 行级处理单元：明细行 + 解析完成的输入 */
    @lombok.AllArgsConstructor
    private static class RowInput {
        private final BatchTaskItem item;
        private final ResolvedPredictionInput resolved;
    }
}
