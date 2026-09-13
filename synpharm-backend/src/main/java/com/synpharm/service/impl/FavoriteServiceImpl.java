package com.synpharm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.dto.response.FavoritePageResponse;
import com.synpharm.dto.response.FavoriteResponse;
import com.synpharm.exception.BusinessException;
import com.synpharm.exception.ErrorCode;
import com.synpharm.model.entity.PredictResult;
import com.synpharm.model.entity.UserFavorite;
import com.synpharm.repository.mapper.PredictResultMapper;
import com.synpharm.repository.mapper.UserFavoriteMapper;
import com.synpharm.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏服务实现（修复方案 5.7）。
 *
 * <p>用户隔离：收藏/查询/取消均以 userId 为边界；
 * 幂等：user_favorite 表唯一键 uk_user_result 兜底 + 查询先行；
 * 归属校验：删除时校验收藏归属，收藏时校验结果归属。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final UserFavoriteMapper favoriteMapper;
    private final PredictResultMapper predictResultMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addFavorite(Long userId, Long resultId, String note) {
        // 只能收藏本人的预测结果（用户隔离）
        PredictResult result = predictResultMapper.selectById(resultId);
        if (result == null) {
            throw new BusinessException(ErrorCode.RESULT_NOT_FOUND);
        }
        if (userId != null && !userId.equals(result.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能收藏自己的预测结果");
        }

        // 幂等：已收藏则直接返回已有收藏ID
        UserFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getResultId, resultId)
        );
        if (existing != null) {
            log.info("重复收藏，幂等返回已有收藏: favoriteId={}, resultId={}", existing.getId(), resultId);
            return existing.getId();
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setResultId(resultId);
        favorite.setNote(note);
        favoriteMapper.insert(favorite);
        log.info("收藏成功: favoriteId={}, userId={}, resultId={}", favorite.getId(), userId, resultId);
        return favorite.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long favoriteId) {
        UserFavorite favorite = favoriteMapper.selectById(favoriteId);
        if (favorite == null) {
            throw new BusinessException("收藏不存在");
        }
        // 归属校验：只能删除自己的收藏
        if (userId != null && !userId.equals(favorite.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该收藏");
        }
        // 物理删除：唯一键 uk_user_result 存在，逻辑删除会导致重复收藏撞键
        favoriteMapper.deletePhysically(favoriteId);
        log.info("取消收藏: favoriteId={}, userId={}", favoriteId, userId);
    }

    @Override
    public FavoritePageResponse listFavorites(Long userId, Integer page, Integer pageSize) {
        // 内存分页（项目未配置 MyBatis-Plus 分页插件，与 ResultServiceImpl 保持一致）
        List<UserFavorite> all = favoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByDesc(UserFavorite::getId)
        );

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int total = all.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(total, from + safeSize);
        List<UserFavorite> sub = from < total ? all.subList(from, to) : List.of();

        // 批量加载结果摘要（逻辑删除的结果自动被 MyBatis-Plus 过滤）
        List<Long> resultIds = sub.stream().map(UserFavorite::getResultId).collect(Collectors.toList());
        Map<Long, PredictResult> resultMap = resultIds.isEmpty() ? Map.of()
                : predictResultMapper.selectBatchIds(resultIds).stream()
                        .collect(Collectors.toMap(PredictResult::getId, Function.identity()));

        List<FavoriteResponse> list = new ArrayList<>();
        for (UserFavorite favorite : sub) {
            list.add(toFavoriteResponse(favorite, resultMap.get(favorite.getResultId())));
        }

        return FavoritePageResponse.builder()
                .total((long) total)
                .page((long) safePage)
                .pageSize((long) safeSize)
                .list(list)
                .build();
    }

    private FavoriteResponse toFavoriteResponse(UserFavorite favorite, PredictResult result) {
        FavoriteResponse.FavoriteResponseBuilder builder = FavoriteResponse.builder()
                .id(favorite.getId())
                .resultId(favorite.getResultId())
                .note(favorite.getNote())
                .createdAt(favorite.getCreatedAt());

        if (result != null) {
            builder.algoType(extractAlgoType(result))
                    .targetId(result.getTargetId())
                    .targetName(result.getTargetName())
                    .bindingAffinity(result.getBindingAffinity())
                    .confidenceScore(result.getConfidenceScore())
                    .confidenceLevel(result.getConfidenceLevel());
        }
        return builder.build();
    }

    private String extractAlgoType(PredictResult result) {
        if (result.getPredictionData() == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(result.getPredictionData());
            if (node.has("algoType") && !node.get("algoType").isNull()) {
                return node.get("algoType").asText();
            }
        } catch (Exception ignored) {
            // 提取失败则 algoType 为 null
        }
        return null;
    }
}
