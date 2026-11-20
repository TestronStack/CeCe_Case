package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.TestCase;
import com.example.demo.TestCaseTemplate;
import com.example.demo.common.ApiException;
import com.example.demo.entity.CaseBatchEntity;
import com.example.demo.entity.CaseItemEntity;
import com.example.demo.mapper.CaseBatchMapper;
import com.example.demo.mapper.CaseItemMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseBatchMapper batchMapper;
    private final CaseItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    public List<CaseBatchEntity> listBatches(Long userId, Long projectId, String keyword) {
        LambdaQueryWrapper<CaseBatchEntity> q = new LambdaQueryWrapper<CaseBatchEntity>()
                .eq(CaseBatchEntity::getUserId, userId)
                .orderByDesc(CaseBatchEntity::getId);
        if (projectId != null) q.eq(CaseBatchEntity::getProjectId, projectId);
        if (keyword != null && !keyword.isBlank()) q.like(CaseBatchEntity::getName, keyword.trim());
        return batchMapper.selectList(q);
    }

    public CaseBatchEntity getBatch(Long userId, Long id) {
        CaseBatchEntity batch = batchMapper.selectById(id);
        if (batch == null || !batch.getUserId().equals(userId)) throw new ApiException(404, "用例批次不存在");
        return batch;
    }

    public void deleteBatch(Long userId, Long id) {
        getBatch(userId, id);
        itemMapper.delete(new LambdaQueryWrapper<CaseItemEntity>().eq(CaseItemEntity::getBatchId, id));
        batchMapper.deleteById(id);
    }

    public List<CaseItemEntity> listCases(Long userId, Long batchId, String priority,
                                          String reviewStatus, String keyword) {
        getBatch(userId, batchId);
        LambdaQueryWrapper<CaseItemEntity> q = new LambdaQueryWrapper<CaseItemEntity>()
                .eq(CaseItemEntity::getBatchId, batchId)
                .orderByAsc(CaseItemEntity::getSortNo);
        if (priority != null && !priority.isBlank()) q.eq(CaseItemEntity::getPriority, priority);
        if (reviewStatus != null && !reviewStatus.isBlank()) q.eq(CaseItemEntity::getReviewStatus, reviewStatus);
        if (keyword != null && !keyword.isBlank()) {
            final String k = keyword.trim();
            q.and(w -> w.like(CaseItemEntity::getTitle, k).or().like(CaseItemEntity::getCaseNo, k));
        }
        return itemMapper.selectList(q);
    }

    public Map<String, Object> batchDetail(Long userId, Long batchId) {
        CaseBatchEntity batch = getBatch(userId, batchId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("columns", parseColumns(batch.getColumnsJson()));
        result.put("cases", listCases(userId, batchId, null, null, null));
        return result;
    }

    public CaseItemEntity updateCase(Long userId, Long caseId, Map<String, Object> body) {
        CaseItemEntity item = itemMapper.selectById(caseId);
        if (item == null || !item.getUserId().equals(userId)) throw new ApiException(404, "用例不存在");

        if (body.get("data") instanceof Map<?, ?> rawData) {
            Map<String, String> loaded = new LinkedHashMap<>();
            try {
                loaded = objectMapper.readValue(item.getDataJson() == null ? "{}" : item.getDataJson(),
                        new TypeReference<LinkedHashMap<String, String>>() {
                        });
            } catch (Exception ignored) {
            }
            final Map<String, String> data = loaded;
            rawData.forEach((k, v) -> data.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
            try {
                item.setDataJson(objectMapper.writeValueAsString(data));
            } catch (Exception e) {
                throw new ApiException(400, "用例数据格式错误");
            }
            if (data.get("title") != null) item.setTitle(data.get("title"));
            if (data.get("priority") != null) item.setPriority(data.get("priority"));
            if (data.get("testCategory") != null) item.setCategory(data.get("testCategory"));
            if (data.get("testPoint") != null && (item.getCategory() == null || item.getCategory().isBlank())) {
                item.setCategory(data.get("testPoint"));
            }
        }
        if (body.get("reviewStatus") != null) item.setReviewStatus(String.valueOf(body.get("reviewStatus")));
        if (body.get("reviewRemark") != null) item.setReviewRemark(String.valueOf(body.get("reviewRemark")));
        itemMapper.updateById(item);
        return item;
    }

    public void deleteCase(Long userId, Long caseId) {
        CaseItemEntity item = itemMapper.selectById(caseId);
        if (item == null || !item.getUserId().equals(userId)) throw new ApiException(404, "用例不存在");
        itemMapper.deleteById(caseId);
        CaseBatchEntity batch = batchMapper.selectById(item.getBatchId());
        if (batch != null) {
            Long count = itemMapper.selectCount(new LambdaQueryWrapper<CaseItemEntity>()
                    .eq(CaseItemEntity::getBatchId, batch.getId()));
            batch.setCaseCount(count == null ? 0 : count.intValue());
            batchMapper.updateById(batch);
        }
    }

    /** 导出当前批次（含人工修改后的内容） */
    public byte[] exportBatch(Long userId, Long batchId, String format) {
        CaseBatchEntity batch = getBatch(userId, batchId);
        String fmt = (format == null || format.isBlank()) ? "xlsx" : format;
        List<Map<String, String>> columns = parseColumns(batch.getColumnsJson());
        StringBuilder header = new StringBuilder();
        for (Map<String, String> col : columns) {
            if (!header.isEmpty()) header.append("|");
            header.append(col.get("displayName"));
        }
        TestCaseTemplate tpl = header.isEmpty()
                ? TestCaseTemplate.createDefaultTemplate()
                : TestCaseTemplate.fromColumns(header.toString());

        List<TestCase> cases = new ArrayList<>();
        for (CaseItemEntity item : listCases(userId, batchId, null, null, null)) {
            TestCase tc = new TestCase(item.getCaseNo());
            try {
                Map<String, String> data = objectMapper.readValue(
                        item.getDataJson() == null ? "{}" : item.getDataJson(),
                        new TypeReference<LinkedHashMap<String, String>>() {
                        });
                tc.setDynamicFields(data);
            } catch (Exception ignored) {
            }
            cases.add(tc);
        }
        return ExcelExporter.exportByFormat(cases, tpl, fmt);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> parseColumns(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, String> caseData(CaseItemEntity item) {
        try {
            return objectMapper.readValue(item.getDataJson() == null ? "{}" : item.getDataJson(),
                    new TypeReference<LinkedHashMap<String, String>>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
