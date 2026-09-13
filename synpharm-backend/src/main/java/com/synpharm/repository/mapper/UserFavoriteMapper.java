package com.synpharm.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.model.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户收藏数据访问接口（修复方案 5.7）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 物理删除收藏（绕过 MyBatis-Plus 逻辑删除）。
     *
     * <p>表存在唯一约束 uk_user_result(user_id, result_id)，逻辑删除后重新收藏
     * 同一结果会撞唯一键，故取消收藏采用物理删除。
     */
    int deletePhysically(@Param("id") Long id);
}
