package com.synpharm.api;

import com.synpharm.dto.response.BatchStatusResponse;
import com.synpharm.dto.response.BatchUploadResponse;
import com.synpharm.service.BatchProcessService;
import com.synpharm.utils.JwtUtils;
import com.synpharm.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "批量预测", description = "批量CSV上传和预测接口")
public class BatchUploadController {

    private final BatchProcessService batchProcessService;
    private final JwtUtils jwtUtils;

    @PostMapping("/upload")
    @Operation(summary = "批量CSV上传", description = "上传CSV文件进行批量预测")
    public Result<BatchUploadResponse> uploadBatch(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("algoType") String algoType) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(batchProcessService.uploadBatch(file, algoType, userId));
    }

    @GetMapping("/status/{batchId}")
    @Operation(summary = "查询批量状态", description = "查询批量任务进度和状态")
    public Result<BatchStatusResponse> getBatchStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String batchId) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(batchProcessService.getBatchStatus(batchId, userId));
    }

    @GetMapping("/download/{batchId}")
    @Operation(summary = "下载批量结果", description = "下载批量预测结果文件")
    public ResponseEntity<?> downloadBatch(
            @RequestHeader("Authorization") String token,
            @PathVariable String batchId) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return batchProcessService.downloadBatch(batchId, userId);
    }

    @GetMapping("/{batchId}/items")
    @Operation(summary = "查询批量任务明细", description = "分页查询批量任务每一行的处理状态（修复方案 5.6）")
    public Result<?> getBatchItems(
            @RequestHeader("Authorization") String token,
            @PathVariable String batchId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(batchProcessService.getBatchItems(batchId, userId, page, pageSize, status));
    }
}
