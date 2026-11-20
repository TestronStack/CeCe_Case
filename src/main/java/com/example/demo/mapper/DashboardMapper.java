package com.example.demo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 工作台统计（聚合查询） */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM test_case_item WHERE user_id = #{userId}")
    long countCases(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM test_case_batch WHERE user_id = #{userId}")
    long countBatches(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM document WHERE user_id = #{userId}")
    long countDocuments(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM generation_task WHERE user_id = #{userId}")
    long countTasks(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM generation_task WHERE user_id = #{userId} AND status = 'SUCCESS'")
    long countSuccessTasks(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM test_case_item WHERE user_id = #{userId} AND review_status = #{status}")
    long countReview(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS total FROM test_case_item " +
            "WHERE user_id = #{userId} AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> dailyCases(@Param("userId") Long userId);

    @Select("SELECT IFNULL(priority, '未标注') AS name, COUNT(*) AS value FROM test_case_item " +
            "WHERE user_id = #{userId} GROUP BY priority")
    List<Map<String, Object>> priorityDistribution(@Param("userId") Long userId);

    @Select("SELECT b.case_type AS name, COUNT(*) AS value FROM test_case_item i " +
            "JOIN test_case_batch b ON i.batch_id = b.id WHERE i.user_id = #{userId} GROUP BY b.case_type")
    List<Map<String, Object>> typeDistribution(@Param("userId") Long userId);
}
