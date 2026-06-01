package dev.adjuva.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.adjuva.model.entity.AutomationSchedule;

import java.time.Instant;
import java.util.List;

public interface AutomationScheduleMapper extends BaseMapper<AutomationSchedule> {

    List<AutomationSchedule> selectByProject(String projectId);

    int markTriggered(String id, Instant lastTriggeredAt, Instant updatedAt);
}
