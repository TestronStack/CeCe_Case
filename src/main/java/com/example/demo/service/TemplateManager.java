package com.example.demo.service;

import com.example.demo.TestCaseTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板管理器（模块4：自定义模板保存与复用）
 * 内存存储，重启后丢失；如需持久化可扩展为文件/数据库存储
 */
@Slf4j
public class TemplateManager {

    private static final Map<String, TestCaseTemplate> TEMPLATES = new ConcurrentHashMap<>();

    static {
        // 内置模板
        TEMPLATES.put("默认模板", TestCaseTemplate.createDefaultTemplate());
        TEMPLATES.put("UI用例", TestCaseTemplate.createUITemplate());
        TEMPLATES.put("接口用例", TestCaseTemplate.createAPITemplate());
    }

    /**
     * 保存模板（同名覆盖）
     */
    public static void save(String name, String columns) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("模板名称不能为空");
        TestCaseTemplate t = TestCaseTemplate.fromColumns(columns);
        t.setTemplateName(name.trim());
        TEMPLATES.put(name.trim(), t);
        log.info("保存模板: {}", name);
    }

    public static void save(String name, TestCaseTemplate template) {
        if (name != null && !name.trim().isEmpty()) TEMPLATES.put(name.trim(), template);
    }

    /**
     * 获取模板，找不到返回null
     */
    public static TestCaseTemplate get(String name) {
        return name == null ? null : TEMPLATES.get(name.trim());
    }

    public static List<String> listNames() {
        return new ArrayList<>(TEMPLATES.keySet());
    }
}
