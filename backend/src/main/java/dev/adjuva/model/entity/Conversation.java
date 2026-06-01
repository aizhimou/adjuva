package dev.adjuva.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("conversations")
public class Conversation {
    @TableId
    private String id;
    private String projectId;
    private String title;
    private String sourceType;
    private String sourceRef;
    private String status;
    private String mailboxId;
    private String workspacePath;
    private String defaultProvider;
    private String defaultModel;
    private String activeRunId;
    private String pendingQuestionMessageId;
    private Instant lastActivityAt;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
