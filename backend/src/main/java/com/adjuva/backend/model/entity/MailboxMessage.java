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
@TableName("mailbox_messages")
public class MailboxMessage {
    @TableId
    private String id;
    private String conversationId;
    private String mailboxId;
    private String runId;
    private String sender;
    private String messageType;
    private String body;
    private String replyToMessageId;
    private String metadataJson;
    private Instant readAt;
    private Instant ackAt;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
