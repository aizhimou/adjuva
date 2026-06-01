package dev.adjuva.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("automation_schedules")
public class AutomationSchedule {
    @TableId
    private String id;
    private String projectId;
    private String title;
    private String status;
    private String scheduleKind;
    private String scheduleExpression;
    private String timezone;
    private String provider;
    private String model;
    private String conversationTitleTemplate;
    private String promptTemplate;
    private Instant lastTriggeredAt;
    private String metadataJson;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
