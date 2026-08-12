package com.example.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用例模板类
 * 支持用户自定义测试用例字段和顺序
 */
public class TestCaseTemplate {
    
    private String templateName;
    private String description;
    private List<TemplateField> fields = new ArrayList<>();
    
    public TestCaseTemplate() {}
    
    public TestCaseTemplate(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<TemplateField> getFields() { return fields; }
    public void setFields(List<TemplateField> fields) { this.fields = fields; }
    
    public void addField(TemplateField field) {
        field.setSortOrder(fields.size());
        fields.add(field);
    }
    
    public void addField(String fieldName, String displayName) {
        addField(fieldName, displayName, FieldType.TEXT, false);
    }
    
    public void addField(String fieldName, String displayName, FieldType fieldType, boolean required) {
        TemplateField field = new TemplateField();
        field.setFieldName(fieldName);
        field.setDisplayName(displayName);
        field.setFieldType(fieldType);
        field.setRequired(required);
        field.setSortOrder(fields.size());
        fields.add(field);
    }
    
    public void addField(String fieldName, String displayName, FieldType fieldType, boolean required, String... enumValues) {
        TemplateField field = new TemplateField();
        field.setFieldName(fieldName);
        field.setDisplayName(displayName);
        field.setFieldType(fieldType);
        field.setRequired(required);
        field.setSortOrder(fields.size());
        if (enumValues != null && enumValues.length > 0) {
            for (String value : enumValues) {
                field.addEnumValue(value);
            }
        }
        fields.add(field);
    }
    
    public List<String> getFieldNames() {
        List<String> names = new ArrayList<>();
        for (TemplateField field : fields) {
            names.add(field.getFieldName());
        }
        return names;
    }
    
    public List<String> getDisplayNames() {
        List<String> names = new ArrayList<>();
        for (TemplateField field : fields) {
            names.add(field.getDisplayName());
        }
        return names;
    }
    
    public String getOutputFormat() {
        StringBuilder format = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) format.append("|");
            format.append(fields.get(i).getDisplayName());
        }
        return format.toString();
    }
    
    public TemplateField getFieldByName(String fieldName) {
        for (TemplateField field : fields) {
            if (field.getFieldName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }
    
    public boolean containsField(String fieldName) {
        return getFieldByName(fieldName) != null;
    }
    
    /**
     * 根据用户自定义列名（|分隔）构建模板
     * 中文列名自动映射到标准字段key，未知列名用 col_N 兜底
     */
    public static TestCaseTemplate fromColumns(String columns) {
        if (columns == null || columns.trim().isEmpty()) {
            return createDefaultTemplate();
        }
        TestCaseTemplate template = new TestCaseTemplate("自定义模板");
        template.setDescription("用户自定义导出模板");
        String[] names = columns.split("[|]");
        for (int i = 0; i < names.length; i++) {
            String display = names[i].trim();
            if (display.isEmpty()) continue;
            String key = mapColumnToField(display);
            if (key == null) key = "col_" + i;
            TemplateField f = new TemplateField();
            f.setFieldName(key);
            f.setDisplayName(display);
            f.setFieldType(FieldType.TEXT);
            f.setSortOrder(i);
            template.getFields().add(f);
        }
        return template;
    }

    /**
     * 将中文列名映射为标准字段key
     */
    public static String mapColumnToField(String displayName) {
        if (displayName == null) return null;
        if (containsAny(displayName, "用例编号", "编号")) return "id";
        if (containsAny(displayName, "用例标题", "标题", "名称")) return "title";
        if (containsAny(displayName, "测试大模块", "大模块", "所属分组", "分组", "测试模块")) return "testModule";
        if (containsAny(displayName, "子模块", "子分组")) return "subModule";
        if (containsAny(displayName, "测试点分类", "测试点", "用例类型")) return "testCategory";
        if (containsAny(displayName, "前置条件", "前置")) return "precondition";
        if (containsAny(displayName, "预置测试数据", "预置数据", "测试数据")) return "presetData";
        if (containsAny(displayName, "操作步骤", "测试步骤", "具体操作", "步骤")) return "steps";
        if (containsAny(displayName, "量化预期结果", "预期结果", "预期")) return "expectedResult";
        if (containsAny(displayName, "优先级", "等级")) return "priority";
        if (containsAny(displayName, "测试环境", "环境")) return "testEnvironment";
        if (containsAny(displayName, "版本")) return "version";
        if (containsAny(displayName, "实际结果")) return "actualResult";
        if (containsAny(displayName, "缺陷单号", "缺陷ID", "缺陷")) return "defectId";
        if (containsAny(displayName, "测试日期", "日期")) return "testDate";
        if (containsAny(displayName, "接口路径", "接口URL", "接口地址", "路径")) return "interfacePath";
        if (containsAny(displayName, "请求方法", "方法")) return "method";
        if (containsAny(displayName, "请求头", "请求头")) return "completeHeaders";
        if (containsAny(displayName, "请求参数", "参数", "请求体")) return "params";
        if (containsAny(displayName, "预期状态码", "状态码")) return "expectedStatusCode";
        if (containsAny(displayName, "预期业务码", "业务码")) return "expectedBusinessCode";
        if (containsAny(displayName, "预期响应JSON", "响应JSON")) return "expectedResponseJson";
        if (containsAny(displayName, "断言规则", "断言")) return "assertionRules";
        if (containsAny(displayName, "数据库校验", "数据库")) return "dbValidation";
        if (containsAny(displayName, "创建人")) return "creator";
        if (containsAny(displayName, "关联需求")) return "relatedRequirement";
        if (containsAny(displayName, "状态")) return "status";
        if (containsAny(displayName, "类型")) return "caseType";
        if (containsAny(displayName, "自动化")) return "isAutomated";
        return null;
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    public static TestCaseTemplate createDefaultTemplate() {
        TestCaseTemplate template = new TestCaseTemplate("默认模板");
        template.setDescription("适用于大多数测试场景的默认模板");
        
        template.addField("title", "用例标题", FieldType.TEXT, true);
        template.addField("testModule", "所属分组", FieldType.TEXT, false);
        template.addField("creator", "创建人", FieldType.TEXT, false);
        template.addField("relatedRequirement", "关联需求", FieldType.TEXT, false);
        template.addField("precondition", "前置条件", FieldType.TEXT, false);
        template.addField("steps", "测试步骤", FieldType.TEXT, false);
        template.addField("expectedResult", "预期结果", FieldType.TEXT, false);
        template.addField("status", "状态", FieldType.ENUM, false, "未执行", "执行中", "通过", "失败", "阻塞");
        template.addField("priority", "等级", FieldType.ENUM, false, "P0", "P1", "P2", "P3");
        template.addField("caseType", "类型", FieldType.ENUM, false, "功能测试", "接口测试", "性能测试", "安全测试", "兼容性测试");
        template.addField("isAutomated", "自动化", FieldType.ENUM, false, "是", "否", "部分");
        
        return template;
    }
    
    public static TestCaseTemplate createUITemplate() {
        TestCaseTemplate template = new TestCaseTemplate("UI业务功能用例");
        template.setDescription("UI测试专用模板");
        
        template.addField("id", "用例编号", FieldType.TEXT, true);
        template.addField("parentModule", "测试大模块", FieldType.TEXT, false);
        template.addField("subModule", "子模块", FieldType.TEXT, false);
        template.addField("testCategory", "测试点分类", FieldType.ENUM, false, "正向", "参数校验", "边界", "异常", "UI校验", "权限校验");
        template.addField("title", "用例标题", FieldType.TEXT, true);
        template.addField("precondition", "前置条件", FieldType.TEXT, false);
        template.addField("presetData", "预置测试数据", FieldType.TEXT, false);
        template.addField("steps", "操作步骤", FieldType.TEXT, false);
        template.addField("expectedResult", "量化预期结果", FieldType.TEXT, false);
        template.addField("priority", "优先级", FieldType.ENUM, false, "P0", "P1", "P2");
        template.addField("testEnvironment", "测试环境", FieldType.TEXT, false);
        template.addField("version", "版本", FieldType.TEXT, false);
        template.addField("actualResult", "实际结果", FieldType.TEXT, false);
        template.addField("defectId", "缺陷单号", FieldType.TEXT, false);
        template.addField("testDate", "测试日期", FieldType.DATE, false);
        
        return template;
    }
    
    public static TestCaseTemplate createAPITemplate() {
        TestCaseTemplate template = new TestCaseTemplate("后端接口自动化用例");
        template.setDescription("接口测试专用模板");
        
        template.addField("id", "用例编号", FieldType.TEXT, true);
        template.addField("testModule", "测试模块", FieldType.TEXT, false);
        template.addField("testPoint", "测试点", FieldType.TEXT, false);
        template.addField("title", "用例标题", FieldType.TEXT, true);
        template.addField("interfacePath", "接口路径", FieldType.TEXT, false);
        template.addField("method", "请求方法", FieldType.ENUM, false, "GET", "POST", "PUT", "DELETE", "PATCH");
        template.addField("precondition", "前置条件", FieldType.TEXT, false);
        template.addField("completeHeaders", "请求头", FieldType.TEXT, false);
        template.addField("params", "请求参数", FieldType.TEXT, false);
        template.addField("expectedStatusCode", "预期状态码", FieldType.NUMBER, false);
        template.addField("expectedBusinessCode", "预期业务码", FieldType.NUMBER, false);
        template.addField("expectedResponseJson", "预期响应JSON", FieldType.TEXT, false);
        template.addField("assertionRules", "断言规则", FieldType.TEXT, false);
        template.addField("dbValidation", "数据库校验", FieldType.TEXT, false);
        template.addField("priority", "优先级", FieldType.ENUM, false, "P0", "P1", "P2");
        template.addField("testEnvironment", "测试环境", FieldType.TEXT, false);
        template.addField("version", "版本", FieldType.TEXT, false);
        template.addField("tester", "测试人", FieldType.TEXT, false);
        template.addField("defectId", "缺陷单号", FieldType.TEXT, false);
        template.addField("testDate", "测试日期", FieldType.DATE, false);
        
        return template;
    }
    
    public static class TemplateField {
        private String fieldName;
        private String displayName;
        private FieldType fieldType;
        private boolean required;
        private List<String> enumValues = new ArrayList<>();
        private int sortOrder;
        
        public TemplateField() {}
        
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public FieldType getFieldType() { return fieldType; }
        public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public List<String> getEnumValues() { return enumValues; }
        public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }
        public void addEnumValue(String value) { this.enumValues.add(value); }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }
    
    public enum FieldType {
        TEXT,
        NUMBER,
        DATE,
        ENUM,
        BOOLEAN
    }
}
