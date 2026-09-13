package com.synpharm.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.client.FastApiClient;
import com.synpharm.dto.response.BatchItemPageResponse;
import com.synpharm.dto.response.BatchPredictionResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchProcessServiceImplTest {

    private static final String VALID_SEQUENCE =
            "MKWVTFISLLFLFSSAYSRGVFRRDTHKSEIAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAK";

    @Mock
    private BatchTaskMapper batchTaskMapper;
    @Mock
    private BatchTaskItemMapper batchTaskItemMapper;
    @Mock
    private PredictTaskMapper predictTaskMapper;
    @Mock
    private PredictResultMapper predictResultMapper;
    @Mock
    private FastApiClient fastApiClient;
    @Mock
    private PredictionInputResolver inputResolver;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private BatchTaskProducer batchTaskProducer;

    @TempDir
    Path tempDir;

    private BatchProcessServiceImpl service;

    private BatchTask task;

    @BeforeEach
    void setUp() {
        service = new BatchProcessServiceImpl(batchTaskMapper, batchTaskItemMapper, predictTaskMapper,
                predictResultMapper, fastApiClient, inputResolver, redisTemplate,
                new ObjectMapper(), batchTaskProducer);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "resultDir", tempDir.toString());
        // 部分用例不触发进度缓存，用 lenient 避免严格模式报未使用桩
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        task = new BatchTask();
        task.setBatchId("b1");
        task.setUserId(1L);
        task.setAlgoType("DTI");
        task.setStatus(0);
        task.setFilePath(tempDir.resolve("input.csv").toString());
        task.setTotalCount(2);
    }

    private BatchTaskItem item(Long id, int rowNumber, String input) {
        BatchTaskItem item = new BatchTaskItem();
        item.setId(id);
        item.setBatchId("b1");
        item.setRowNumber(rowNumber);
        item.setInputValue(input);
        item.setStatus(0);
        return item;
    }

    private ResolvedPredictionInput dtiInput() {
        return ResolvedPredictionInput.builder()
                .algoType("DTI")
                .inputType("smiles")
                .ligandSmiles("CCO")
                .targetSequence(VALID_SEQUENCE)
                .build();
    }

    private void stubInsertGeneratesIds() {
        AtomicLong taskId = new AtomicLong(100);
        doAnswer(inv -> {
            PredictTask t = inv.getArgument(0);
            t.setId(taskId.incrementAndGet());
            return 1;
        }).when(predictTaskMapper).insert(any(PredictTask.class));

        AtomicLong resultId = new AtomicLong(200);
        doAnswer(inv -> {
            PredictResult r = inv.getArgument(0);
            r.setId(resultId.incrementAndGet());
            return 1;
        }).when(predictResultMapper).insert(any(PredictResult.class));
    }

    @Test
    void 全链路闭环_成功行落库回写resultId_失败行记录错误码() {
        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(task);
        BatchTaskItem item1 = item(1L, 1, "CCO," + VALID_SEQUENCE);
        BatchTaskItem item2 = item(2L, 2, "CC,SEQ");
        when(batchTaskItemMapper.selectList(any())).thenReturn(List.of(item1, item2));
        when(inputResolver.resolve(anyString(), anyString(), anyString())).thenReturn(dtiInput());
        stubInsertGeneratesIds();

        BatchPredictionResponse batchResponse = new BatchPredictionResponse();
        batchResponse.setStatus("success");
        batchResponse.setResults(List.of(
                Map.of("target_id", "T1", "target_name", "靶点1", "binding_affinity", 1.2,
                        "confidence_score", 0.9, "confidence_level", "high"),
                Map.of("error", "该行预测失败")
        ));
        when(fastApiClient.predictBatch(anyList(), eq("DTI"))).thenReturn(batchResponse);

        when(batchTaskItemMapper.selectCount(any())).thenReturn(1L, 1L);

        service.processBatch("b1", "DTI");

        // 批次成功，统计正确（1 成功 1 失败）
        assertEquals(2, task.getStatus());
        assertEquals(1, task.getSuccessCount());
        assertEquals(1, task.getFailCount());

        // 成功行：状态 2 + result_id 回写
        assertEquals(2, item1.getStatus());
        assertEquals(201L, item1.getResultId());
        verify(predictTaskMapper).insert(any(PredictTask.class));
        verify(predictResultMapper).insert(argThat(r -> "batch-predict".equals(r.getDatasetSource())));

        // 失败行：状态 3 + 字符串错误码
        assertEquals(3, item2.getStatus());
        assertEquals("MODEL_UNAVAILABLE", item2.getErrorCode());
        assertEquals("该行预测失败", item2.getErrorMessage());

        // 结果 CSV 已生成（下载功能保留）
        assertTrue(Files.exists(tempDir.resolve("b1_result.csv")));
    }

    @Test
    void 行级解析失败_不影响其他行() {
        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(task);
        BatchTaskItem item1 = item(1L, 1, "CCO," + VALID_SEQUENCE);
        BatchTaskItem item2 = item(2L, 2, "C((");
        when(batchTaskItemMapper.selectList(any())).thenReturn(List.of(item1, item2));
        when(inputResolver.resolve(anyString(), anyString(), anyString()))
                .thenReturn(dtiInput())
                .thenThrow(new PredictionException(PredictionErrorCode.INVALID_SMILES, "SMILES 括号不配平"));
        stubInsertGeneratesIds();

        BatchPredictionResponse batchResponse = new BatchPredictionResponse();
        batchResponse.setResults(List.of(
                Map.of("target_id", "T1", "binding_affinity", 1.2, "confidence_score", 0.9, "confidence_level", "high")
        ));
        when(fastApiClient.predictBatch(anyList(), eq("DTI"))).thenReturn(batchResponse);
        when(batchTaskItemMapper.selectCount(any())).thenReturn(1L, 1L);

        service.processBatch("b1", "DTI");

        assertEquals(2, task.getStatus());
        assertEquals(2, item1.getStatus());
        assertEquals(3, item2.getStatus());
        assertEquals("INVALID_SMILES", item2.getErrorCode());
        // 只对解析成功的 1 行调用了批量预测
        verify(fastApiClient).predictBatch(argThat(list -> list.size() == 1), eq("DTI"));
    }

    @Test
    void 批次级失败_状态置FAIL并向上抛出_供消费者进死信() {
        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(task);
        BatchTaskItem item1 = item(1L, 1, "CCO," + VALID_SEQUENCE);
        when(batchTaskItemMapper.selectList(any())).thenReturn(List.of(item1));
        when(inputResolver.resolve(anyString(), anyString(), anyString())).thenReturn(dtiInput());
        when(fastApiClient.predictBatch(anyList(), eq("DTI")))
                .thenThrow(new RuntimeException("批量预测服务调用失败"));

        assertThrows(RuntimeException.class, () -> service.processBatch("b1", "DTI"));

        assertEquals(3, task.getStatus());
        assertEquals(3, item1.getStatus());
        assertEquals("FASTAPI_UNAVAILABLE", item1.getErrorCode());
    }

    @Test
    void 已完成批次_幂等跳过() {
        task.setStatus(2);
        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(task);

        service.processBatch("b1", "DTI");

        verify(fastApiClient, never()).predictBatch(anyList(), anyString());
        verify(batchTaskItemMapper, never()).selectList(any());
    }

    @Test
    void 旧批次无明细_从CSV补建明细后闭环处理() throws Exception {
        Files.writeString(Path.of(task.getFilePath()), "drug_smiles,target_seq\nCCO," + VALID_SEQUENCE + "\n");

        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(task);
        when(batchTaskItemMapper.selectList(any())).thenReturn(List.of());
        when(inputResolver.resolve(anyString(), anyString(), anyString())).thenReturn(dtiInput());
        stubInsertGeneratesIds();

        AtomicLong itemId = new AtomicLong(0);
        doAnswer(inv -> {
            BatchTaskItem item = inv.getArgument(0);
            item.setId(itemId.incrementAndGet());
            return 1;
        }).when(batchTaskItemMapper).insert(any(BatchTaskItem.class));

        BatchPredictionResponse batchResponse = new BatchPredictionResponse();
        batchResponse.setResults(List.of(
                Map.of("target_id", "T1", "binding_affinity", 1.2, "confidence_score", 0.9, "confidence_level", "high")
        ));
        when(fastApiClient.predictBatch(anyList(), eq("DTI"))).thenReturn(batchResponse);
        when(batchTaskItemMapper.selectCount(any())).thenReturn(1L, 0L);

        service.processBatch("b1", "DTI");

        assertEquals(2, task.getStatus());
        assertEquals(1, task.getSuccessCount());
        // 补建了 1 条明细
        verify(batchTaskItemMapper, atLeastOnce()).insert(any(BatchTaskItem.class));
    }

    @Test
    void 查询明细_归属校验_他人批次被拒绝() {
        BatchTask other = new BatchTask();
        other.setBatchId("b1");
        other.setUserId(2L);
        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(other);

        assertThrows(BusinessException.class,
                () -> service.getBatchItems("b1", 1L, 1, 10, null));
    }

    @Test
    void 查询明细_内存分页() {
        when(batchTaskMapper.selectByBatchId("b1")).thenReturn(task);
        BatchTaskItem i1 = item(1L, 1, "A,B");
        i1.setStatus(2);
        BatchTaskItem i2 = item(2L, 2, "C,D");
        i2.setStatus(3);
        i2.setErrorCode("INVALID_SMILES");
        BatchTaskItem i3 = item(3L, 3, "E,F");
        i3.setStatus(2);
        when(batchTaskItemMapper.selectList(any())).thenReturn(List.of(i1, i2, i3));

        BatchItemPageResponse page = service.getBatchItems("b1", 1L, 2, 1, null);

        assertEquals(3L, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals(2, page.getList().get(0).getRowNumber());
        assertEquals("FAIL", page.getList().get(0).getStatusText());
        assertEquals("INVALID_SMILES", page.getList().get(0).getErrorCode());

        // 按状态过滤
        service.getBatchItems("b1", 1L, 1, 10, 2);
        verify(batchTaskItemMapper, times(2)).selectList(any());
    }
}
