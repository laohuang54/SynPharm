package com.synpharm.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.dto.response.FavoritePageResponse;
import com.synpharm.exception.BusinessException;
import com.synpharm.exception.ErrorCode;
import com.synpharm.model.entity.PredictResult;
import com.synpharm.model.entity.UserFavorite;
import com.synpharm.repository.mapper.PredictResultMapper;
import com.synpharm.repository.mapper.UserFavoriteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock
    private UserFavoriteMapper favoriteMapper;

    @Mock
    private PredictResultMapper predictResultMapper;

    private FavoriteServiceImpl service;

    private PredictResult ownResult;
    private PredictResult otherResult;

    @BeforeEach
    void setUp() {
        service = new FavoriteServiceImpl(favoriteMapper, predictResultMapper, new ObjectMapper());

        ownResult = new PredictResult();
        ownResult.setId(10L);
        ownResult.setUserId(1L);
        ownResult.setTargetId("T1");
        ownResult.setTargetName("靶点1");
        ownResult.setBindingAffinity(1.23);
        ownResult.setConfidenceScore(0.9);
        ownResult.setConfidenceLevel("high");
        ownResult.setPredictionData("{\"algoType\":\"DTI\"}");

        otherResult = new PredictResult();
        otherResult.setId(20L);
        otherResult.setUserId(2L);
    }

    @Test
    void 收藏本人结果_成功() {
        when(predictResultMapper.selectById(10L)).thenReturn(ownResult);
        when(favoriteMapper.selectOne(any())).thenReturn(null);
        // 模拟 MyBatis-Plus 插入回填主键
        doAnswer(inv -> {
            UserFavorite f = inv.getArgument(0);
            f.setId(1L);
            return 1;
        }).when(favoriteMapper).insert(any(UserFavorite.class));

        Long favoriteId = service.addFavorite(1L, 10L, "备注");

        assertEquals(1L, favoriteId);
        verify(favoriteMapper).insert(argThat(f -> f.getUserId() == 1L && f.getResultId() == 10L && "备注".equals(f.getNote())));
    }

    @Test
    void 重复收藏_幂等返回已有收藏() {
        UserFavorite existing = new UserFavorite();
        existing.setId(5L);
        when(predictResultMapper.selectById(10L)).thenReturn(ownResult);
        when(favoriteMapper.selectOne(any())).thenReturn(existing);

        Long favoriteId = service.addFavorite(1L, 10L, null);

        assertEquals(5L, favoriteId);
        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void 收藏不存在的结果_抛RESULT_NOT_FOUND() {
        when(predictResultMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addFavorite(1L, 99L, null));
        assertEquals(ErrorCode.RESULT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void 收藏他人结果_被拒绝() {
        when(predictResultMapper.selectById(20L)).thenReturn(otherResult);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addFavorite(1L, 20L, null));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
    }

    @Test
    void 取消本人收藏_物理删除() {
        UserFavorite favorite = new UserFavorite();
        favorite.setId(7L);
        favorite.setUserId(1L);
        when(favoriteMapper.selectById(7L)).thenReturn(favorite);

        service.removeFavorite(1L, 7L);

        verify(favoriteMapper).deletePhysically(7L);
    }

    @Test
    void 取消他人收藏_被拒绝() {
        UserFavorite favorite = new UserFavorite();
        favorite.setId(7L);
        favorite.setUserId(2L);
        when(favoriteMapper.selectById(7L)).thenReturn(favorite);

        assertThrows(BusinessException.class, () -> service.removeFavorite(1L, 7L));
        verify(favoriteMapper, never()).deletePhysically(anyLong());
    }

    @Test
    void 取消不存在的收藏_报错() {
        when(favoriteMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.removeFavorite(1L, 9L));
    }

    @Test
    void 我的收藏_分页返回并带结果摘要() {
        UserFavorite f1 = new UserFavorite();
        f1.setId(1L);
        f1.setUserId(1L);
        f1.setResultId(10L);
        f1.setNote("n1");
        f1.setCreatedAt(LocalDateTime.of(2026, 9, 9, 10, 0));
        UserFavorite f2 = new UserFavorite();
        f2.setId(2L);
        f2.setUserId(1L);
        f2.setResultId(20L);

        when(favoriteMapper.selectList(any())).thenReturn(List.of(f1, f2));
        when(predictResultMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ownResult));

        FavoritePageResponse page = service.listFavorites(1L, 2, 1);

        assertEquals(2L, page.getTotal());
        assertEquals(1, page.getList().size());
        // 第 2 页第 1 条是 f2，其结果缺失（模拟已被删除），摘要字段为空
        assertEquals(2L, page.getList().get(0).getId());
        assertNull(page.getList().get(0).getAlgoType());

        // 第 1 页应带结果摘要
        FavoritePageResponse firstPage = service.listFavorites(1L, 1, 1);
        assertEquals(1L, firstPage.getList().get(0).getId());
        assertEquals("DTI", firstPage.getList().get(0).getAlgoType());
        assertEquals("T1", firstPage.getList().get(0).getTargetId());
    }
}
