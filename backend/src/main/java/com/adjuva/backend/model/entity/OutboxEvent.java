package com.adjuva.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("outbox_events")
public class OutboxEvent {
    @TableId
    private String id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payloadJson;
    private String status;
    private Integer attempts;
    private Instant availableAt;
    private Instant publishedAt;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
