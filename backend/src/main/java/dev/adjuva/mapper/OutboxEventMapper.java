package dev.adjuva.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.adjuva.model.entity.OutboxEvent;

import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    List<OutboxEvent> selectPendingAfter(String afterEventId, int limit);
}
