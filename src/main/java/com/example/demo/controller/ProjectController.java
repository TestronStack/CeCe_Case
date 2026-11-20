package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.ProjectEntity;
import com.example.demo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<ProjectEntity>> list() {
        return ApiResponse.ok(projectService.list(UserContext.userId()));
    }

    @PostMapping
    public ApiResponse<ProjectEntity> create(@RequestBody Map<String, String> body) {
        return ApiResponse.ok("项目已创建", projectService.create(UserContext.userId(),
                body.get("name"), body.get("description"), body.get("color")));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectEntity> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok("项目已更新", projectService.update(UserContext.userId(), id,
                body.get("name"), body.get("description"), body.get("color")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(UserContext.userId(), id);
        return ApiResponse.ok("项目已删除", null);
    }
}
