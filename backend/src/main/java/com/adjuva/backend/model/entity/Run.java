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
@TableName("runs")
public class Run {
    @TableId
    private String id;
    private String conversationId;
    private String providerSessionId;
    private String triggerMessageId;
    private String triggerType;
    private String provider;
    private String model;
    private String lifecycle;
    private String terminationReason;
    private String processId;
    private String externalSessionIdBefore;
    private String externalSessionIdAfter;
    private Instant startedAt;
    private Instant endedAt;
    private Integer exitCode;
    private String signal;
    private String finalEventType;
    private String timeoutKind;
    private String errorMessage;
    private String prompt;
    private String commandLine;
    private String metadataJson;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
