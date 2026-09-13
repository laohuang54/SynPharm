package com.synpharm.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏实体。
 *
 * <p>对应用户收藏表 user_favorite，记录用户对预测结果的收藏。
 * 表已存在唯一约束 uk_user_result(user_id, result_id)；
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
