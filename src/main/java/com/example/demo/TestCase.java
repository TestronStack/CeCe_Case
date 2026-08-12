package com.example.demo;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试用例实体类
 * 支持动态字段，通过Map存储用户自定义的字段值
 */
public class TestCase {
    
    private String id;
    private String testCaseType;
    private Map<String, String> dynamicFields = new HashMap<>();

    public TestCase() {}
    
    public TestCase(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTestCaseType() { return testCaseType; }
    public void setTestCaseType(String testCaseType) { this.testCaseType = testCaseType; }
    public Map<String, String> getDynamicFields() { return dynamicFields; }
    public void setDynamicFields(Map<String, String> dynamicFields) { this.dynamicFields = dynamicFields; }
    
    public String getField(String fieldName) {
        return dynamicFields.get(fieldName);
    }
    
    public void setField(String fieldName, String value) {
        dynamicFields.put(fieldName, value);
    }
    
    public void removeField(String fieldName) {
        dynamicFields.remove(fieldName);
    }
    
    public boolean hasField(String fieldName) {
        return dynamicFields.containsKey(fieldName);
    }
    
    public Integer getIntField(String fieldName) {
        String value = getField(fieldName);
        return value != null && !value.isEmpty() ? Integer.parseInt(value) : null;
    }
    
    public void setIntField(String fieldName, Integer value) {
        setField(fieldName, value != null ? value.toString() : "");
    }
    
    public boolean getBooleanField(String fieldName) {
        String value = getField(fieldName);
        return "是".equals(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
    }
    
    public void setBooleanField(String fieldName, boolean value) {
        setField(fieldName, value ? "是" : "否");
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id);
        if (testCaseType != null) {
            sb.append(" | ").append(testCaseType);
        }
        String title = getField("title");
        if (title != null) {
            sb.append(" | ").append(title);
        }
        return sb.toString();
    }
    
    // ===== 兼容层：保留旧的getter/setter方法 =====
    
    public String getTestModule() { return getField("testModule"); }
    public void setTestModule(String value) { setField("testModule", value); }
    
    public String getTestPoint() { return getField("testPoint"); }
    public void setTestPoint(String value) { setField("testPoint", value); }
    
    public String getTitle() { return getField("title"); }
    public void setTitle(String value) { setField("title", value); }
    
    public String getPrecondition() { return getField("precondition"); }
    public void setPrecondition(String value) { setField("precondition", value); }
    
    public String getInterfacePath() { return getField("interfacePath"); }
    public void setInterfacePath(String value) { setField("interfacePath", value); }
    
    public String getMethod() { return getField("method"); }
    public void setMethod(String value) { setField("method", value); }
    
    public String getCompleteHeaders() { return getField("completeHeaders"); }
    public void setCompleteHeaders(String value) { setField("completeHeaders", value); }
    
    public String getParams() { return getField("params"); }
    public void setParams(String value) { setField("params", value); }
    
    public Integer getExpectedStatusCode() {
        String value = getField("expectedStatusCode");
        return value != null && !value.isEmpty() ? Integer.parseInt(value) : null;
    }
    public void setExpectedStatusCode(Integer value) { setField("expectedStatusCode", value != null ? value.toString() : ""); }
    
    public Integer getExpectedBusinessCode() {
        String value = getField("expectedBusinessCode");
        return value != null && !value.isEmpty() ? Integer.parseInt(value) : null;
    }
    public void setExpectedBusinessCode(Integer value) { setField("expectedBusinessCode", value != null ? value.toString() : ""); }
    
    public String getExpectedResponseJson() { return getField("expectedResponseJson"); }
    public void setExpectedResponseJson(String value) { setField("expectedResponseJson", value); }
    
    public String getAssertionRules() { return getField("assertionRules"); }
    public void setAssertionRules(String value) { setField("assertionRules", value); }
    
    public String getDbValidation() { return getField("dbValidation"); }
    public void setDbValidation(String value) { setField("dbValidation", value); }
    
    public String getPriority() { return getField("priority"); }
    public void setPriority(String value) { setField("priority", value); }
    
    public String getTestEnvironment() { return getField("testEnvironment"); }
    public void setTestEnvironment(String value) { setField("testEnvironment", value); }
    
    public String getVersion() { return getField("version"); }
    public void setVersion(String value) { setField("version", value); }
    
    public String getTester() { return getField("tester"); }
    public void setTester(String value) { setField("tester", value); }
    
    public String getDefectId() { return getField("defectId"); }
    public void setDefectId(String value) { setField("defectId", value); }
    
    public String getTestDate() { return getField("testDate"); }
    public void setTestDate(String value) { setField("testDate", value); }
    
    public String getApiRelatedUiCaseId() { return getField("apiRelatedUiCaseId"); }
    public void setApiRelatedUiCaseId(String value) { setField("apiRelatedUiCaseId", value); }
    
    public String getUiRelatedApiCaseId() { return getField("uiRelatedApiCaseId"); }
    public void setUiRelatedApiCaseId(String value) { setField("uiRelatedApiCaseId", value); }
    
    public String getActualResult() { return getField("actualResult"); }
    public void setActualResult(String value) { setField("actualResult", value); }
    
    public String getTestModuleCode() { return getField("testModuleCode"); }
    public void setTestModuleCode(String value) { setField("testModuleCode", value); }
    
    public String getParentModule() { return getField("parentModule"); }
    public void setParentModule(String value) { setField("parentModule", value); }
    
    public String getSubModule() { return getField("subModule"); }
    public void setSubModule(String value) { setField("subModule", value); }
    
    public String getTestCategory() { return getField("testCategory"); }
    public void setTestCategory(String value) { setField("testCategory", value); }
    
    public String getPresetData() { return getField("presetData"); }
    public void setPresetData(String value) { setField("presetData", value); }
    
    public String getSteps() { return getField("steps"); }
    public void setSteps(String value) { setField("steps", value); }
    
    public String getExpectedResult() { return getField("expectedResult"); }
    public void setExpectedResult(String value) { setField("expectedResult", value); }
    
    public String getCustomRowData() { return getField("customRowData"); }
    public void setCustomRowData(String value) { setField("customRowData", value); }
}
