package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("test_case_batch")
public class CaseBatchEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private Long documentId;
    private Long taskId;
    private String name;
    private String caseType;
    private String columnsJson;
    private Integer caseCount;
    private String outputFormat;
    private String filePath;
    private String scriptLanguage;
    private String scriptPath;
    private LocalDateTime createdAt;
}
