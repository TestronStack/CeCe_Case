package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.ApiException;
import com.example.demo.entity.ProjectEntity;
import com.example.demo.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public List<ProjectEntity> list(Long userId) {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId)
                .orderByDesc(ProjectEntity::getUpdatedAt));
    }

    public ProjectEntity get(Long userId, Long id) {
        ProjectEntity p = projectMapper.selectById(id);
        if (p == null || !p.getUserId().equals(userId)) throw new ApiException(404, "项目不存在");
        return p;
    }

    public ProjectEntity create(Long userId, String name, String description, String color) {
        if (name == null || name.isBlank()) throw new ApiException("项目名称不能为空");
        ProjectEntity p = new ProjectEntity();
        p.setUserId(userId);
        p.setName(name.trim());
        p.setDescription(description);
        p.setColor(color == null || color.isBlank() ? "#2563eb" : color);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);
        return p;
    }

    public ProjectEntity update(Long userId, Long id, String name, String description, String color) {
        ProjectEntity p = get(userId, id);
        if (name != null && !name.isBlank()) p.setName(name.trim());
        if (description != null) p.setDescription(description);
        if (color != null && !color.isBlank()) p.setColor(color);
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        return p;
    }

    public void delete(Long userId, Long id) {
        get(userId, id);
        projectMapper.deleteById(id);
    }

    public void ensureDefault(Long userId) {
        Long count = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId));
        if (count == null || count == 0) {
            create(userId, "默认项目", "系统自动创建的默认项目空间", "#2563eb");
        }
    }

    public void touch(Long userId, Long projectId) {
        if (projectId == null) return;
        ProjectEntity p = projectMapper.selectById(projectId);
        if (p != null && p.getUserId().equals(userId)) {
            p.setUpdatedAt(LocalDateTime.now());
            projectMapper.updateById(p);
        }
    }
}
