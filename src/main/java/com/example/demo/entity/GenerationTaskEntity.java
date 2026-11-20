package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_task")
public class GenerationTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private Long documentId;
    private Long batchId;
    private String taskType;
    private String title;
    private String status;
    private Integer progress;
    private String stage;
    private String message;
    private String paramsJson;
    private String model;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
