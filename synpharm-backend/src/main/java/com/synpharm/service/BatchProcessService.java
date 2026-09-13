package com.synpharm.service;

import com.synpharm.dto.response.BatchItemPageResponse;
import com.synpharm.dto.response.BatchStatusResponse;
import com.synpharm.dto.response.BatchUploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface BatchProcessService {

    BatchUploadResponse uploadBatch(MultipartFile file, String algoType, Long userId);

    void processBatch(String batchId, String algoType);

    BatchStatusResponse getBatchStatus(String batchId, Long userId);

    ResponseEntity<org.springframework.core.io.Resource> downloadBatch(String batchId, Long userId);

    /**
     * 分页查询批量任务明细（修复方案 5.6 新增，含归属校验）。
     *
     * @param batchId  批次ID
     * @param userId   当前用户ID
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @param status   按状态过滤（可选：0/1/2/3）
     * @return 明细分页
     */
    BatchItemPageResponse getBatchItems(String batchId, Long userId, Integer page, Integer pageSize, Integer status);
}
