package com.synpharm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏响应（修复方案 5.7）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResponse {

    /** 收藏ID */
    private Long id;

    /** 关联的预测结果ID */
    private Long resultId;

    /** 备注 */
    private String note;

    /** 收藏时间 */
    private LocalDateTime createdAt;

    // ===== 收藏结果的摘要信息（供列表直接展示） =====

    private String algoType;

    private String targetId;

    private String targetName;

    private Double bindingAffinity;

    private Double confidenceScore;

    private String confidenceLevel;
}
