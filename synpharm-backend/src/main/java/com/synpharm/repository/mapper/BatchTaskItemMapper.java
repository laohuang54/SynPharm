package com.synpharm.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.model.entity.BatchTaskItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批量任务明细数据访问接口（修复方案 5.6）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Mapper
public interface BatchTaskItemMapper extends BaseMapper<BatchTaskItem> {
}
