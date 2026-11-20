package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.TestCaseTemplate;
import com.example.demo.common.ApiException;
import com.example.demo.entity.UserTemplateEntity;
import com.example.demo.mapper.UserTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final UserTemplateMapper templateMapper;

    public List<UserTemplateEntity> list(Long userId) {
        return templateMapper.selectList(new LambdaQueryWrapper<UserTemplateEntity>()
                .eq(UserTemplateEntity::getUserId, userId)
                .orderByDesc(UserTemplateEntity::getId));
    }

    public UserTemplateEntity save(Long userId, String name, String columns) {
        if (name == null || name.isBlank()) throw new ApiException("模板名称不能为空");
        if (columns == null || columns.isBlank()) throw new ApiException("列定义不能为空");
        UserTemplateEntity entity = new UserTemplateEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setColumnsText(columns.trim());
        entity.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(entity);
        return entity;
    }

    public void delete(Long userId, Long id) {
        UserTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) throw new ApiException(404, "模板不存在");
        templateMapper.deleteById(id);
    }

    /** 内置标准模板（只读展示） */
    public List<Map<String, Object>> builtins() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(describe(TestCaseTemplate.createUITemplate(), "UI"));
        list.add(describe(TestCaseTemplate.createAPITemplate(), "API"));
        list.add(describe(TestCaseTemplate.createDefaultTemplate(), "DEFAULT"));
        return list;
    }

    private Map<String, Object> describe(TestCaseTemplate tpl, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", tpl.getTemplateName());
        m.put("description", tpl.getDescription());
        List<Map<String, String>> cols = new ArrayList<>();
        for (TestCaseTemplate.TemplateField f : tpl.getFields()) {
            Map<String, String> col = new LinkedHashMap<>();
            col.put("fieldName", f.getFieldName());
            col.put("displayName", f.getDisplayName());
            cols.add(col);
        }
        m.put("columns", cols);
        return m;
    }
}
