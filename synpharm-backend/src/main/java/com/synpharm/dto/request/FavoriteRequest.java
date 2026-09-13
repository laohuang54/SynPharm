package com.synpharm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收藏请求（修复方案 5.7）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
public class FavoriteRequest {

    @NotNull(message = "结果ID不能为空")
    private Long resultId;

    /** 备注（可选） */
    private String note;
}
