package com.example.demo.service;

import com.example.demo.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    public Map<String, Object> overview(Long userId) {
        long tasks = dashboardMapper.countTasks(userId);
        long success = dashboardMapper.countSuccessTasks(userId);
        long cases = dashboardMapper.countCases(userId);
        long approved = dashboardMapper.countReview(userId, "APPROVED");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("caseCount", cases);
        stats.put("batchCount", dashboardMapper.countBatches(userId));
        stats.put("documentCount", dashboardMapper.countDocuments(userId));
        stats.put("taskCount", tasks);
        stats.put("successRate", tasks == 0 ? 0 : Math.round(success * 1000.0 / tasks) / 10.0);
        stats.put("passRate", cases == 0 ? 0 : Math.round(approved * 1000.0 / cases) / 10.0);
        stats.put("pendingReview", dashboardMapper.countReview(userId, "PENDING"));

        // 补齐最近 7 天没有数据的日期
        Map<String, Long> dailyMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dashboardMapper.dailyCases(userId)) {
            dailyMap.put(String.valueOf(row.get("day")), ((Number) row.get("total")).longValue());
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).toString();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", LocalDate.now().minusDays(i).format(fmt));
            point.put("value", dailyMap.getOrDefault(key, 0L));
            daily.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("daily", daily);
        result.put("priority", dashboardMapper.priorityDistribution(userId));
        result.put("typeDistribution", dashboardMapper.typeDistribution(userId));
        return result;
    }
}
