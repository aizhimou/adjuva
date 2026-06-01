package com.adjuva.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adjuva.backend.model.entity.OutboxEvent;

import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    List<OutboxEvent> selectPendingAfter(String afterEventId, int limit);
}
