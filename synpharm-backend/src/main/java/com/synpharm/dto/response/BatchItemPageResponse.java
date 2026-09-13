package com.synpharm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量任务明细分页响应（修复方案 5.6）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchItemPageResponse {

    private Long total;

    private Long page;

    private Long pageSize;

    private List<BatchItemResponse> list;
}
