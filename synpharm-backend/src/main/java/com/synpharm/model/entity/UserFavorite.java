package com.synpharm.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏实体（修复方案 5.7）。
 *
 * <p>映射数据库表 user_favorite。表已存在唯一约束 uk_user_result(user_id, result_id)；
 * 取消收藏采用物理删除（见 UserFavoriteMapper.deletePhysically），
 * 避免逻辑删除后重新收藏同一结果时撞唯一键。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@TableName("user_favorite")
public class UserFavorite {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("result_id")
    private Long resultId;

    @TableField("note")
    private String note;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
