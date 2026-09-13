package com.synpharm.service;

import com.synpharm.dto.response.FavoritePageResponse;

/**
 * 收藏服务（修复方案 5.7）。
 *
 * <p>保证：按用户隔离、重复收藏幂等、删除时校验收藏归属。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
public interface FavoriteService {

    /**
     * 收藏预测结果。
     *
     * <p>仅可收藏本人的结果；重复收藏同一结果幂等（直接返回已有收藏ID）。
     *
     * @param userId   当前用户ID
     * @param resultId 结果ID
     * @param note     备注（可选）
     * @return 收藏ID
     */
    Long addFavorite(Long userId, Long resultId, String note);

    /**
     * 取消收藏（物理删除，校验收藏归属）。
     *
     * @param userId     当前用户ID
     * @param favoriteId 收藏ID
     */
    void removeFavorite(Long userId, Long favoriteId);

    /**
     * 分页查询我的收藏（内存分页，与项目既有惯例一致）。
     *
     * @param userId   当前用户ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 收藏分页
     */
    FavoritePageResponse listFavorites(Long userId, Integer page, Integer pageSize);
}
