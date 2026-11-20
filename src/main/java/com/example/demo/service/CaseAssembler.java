package com.example.demo.service;

import com.example.demo.TestCase;
import com.example.demo.TestCaseTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 用例字段转置：把 AI 输出的标准字段映射到模板列，
 * 补充创建人/关联需求/默认值，并按全字段指纹去重。
 */
public final class CaseAssembler {

    private CaseAssembler() {
    }

    public static List<TestCase> transpose(List<TestCase> cases, TestCaseTemplate tpl,
                                           String creator, String relatedRequirement) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<TestCase> result = new ArrayList<>();
        for (TestCase tc : cases) {
            Map<String, String> std = new HashMap<>(tc.getDynamicFields());
            Map<String, String> transposed = new LinkedHashMap<>();
            for (TestCaseTemplate.TemplateField f : tpl.getFields()) {
                String key = f.getFieldName();
                String val = std.get(key);
                if (val == null && "id".equals(key)) val = tc.getId();
                if (val == null || val.isEmpty()) val = findStandardValue(std, f.getDisplayName());
                transposed.put(key, val == null ? "" : val);
            }
            tc.setDynamicFields(transposed);
            normalizeNumericField(tc, "expectedStatusCode", true);
            normalizeNumericField(tc, "expectedBusinessCode", false);
            tc.setField("creator", creator == null ? "" : creator);
            tc.setField("relatedRequirement", relatedRequirement == null ? "" : relatedRequirement);
            tc.setField("isAutomated", "");
            tc.setField("caseType", "功能测试");

            StringBuilder fingerprint = new StringBuilder();
            for (Map.Entry<String, String> e : tc.getDynamicFields().entrySet()) {
                if ("id".equals(e.getKey()) || "customRowData".equals(e.getKey())) continue;
                fingerprint.append(e.getKey()).append("=")
                        .append(e.getValue() == null ? "" : e.getValue()
                                .replaceAll("【[^】]*】", "").replaceAll("\\s+", "")).append("|");
            }
            if (seen.add(fingerprint.toString())) result.add(tc);
        }
        return result;
    }

    private static String findStandardValue(Map<String, String> std, String displayName) {
        if (displayName == null) return null;
        if (displayName.contains("状态码") || displayName.contains("业务码")) return null;
        if (displayName.contains("预期结果") || displayName.contains("预期")) {
            String v = std.get("expectedResult");
            if (v == null || v.isEmpty()) v = std.get("expectedResponseJson");
            return v;
        }
        if (displayName.contains("步骤") || displayName.contains("操作")) return std.get("steps");
        if (displayName.contains("分组") || displayName.contains("模块")) return std.get("testModule");
        if (displayName.contains("等级") || displayName.contains("优先级")) return std.get("priority");
        if (displayName.contains("状态")) return std.get("status");
        return null;
    }

    private static void normalizeNumericField(TestCase tc, String field, boolean statusCode) {
        String v = tc.getField(field);
        if (v == null || v.isEmpty() || v.trim().matches("\\d+")) return;
        Integer code = statusCode ? TestCase.extractStatusCode(v) : TestCase.extractInteger(v);
        tc.setField(field, code != null ? code.toString() : "");
    }
}
