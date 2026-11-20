package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.UserTemplateEntity;
import com.example.demo.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        return ApiResponse.ok(Map.of(
                "mine", templateService.list(UserContext.userId()),
                "builtins", templateService.builtins()
        ));
    }

    @PostMapping
    public ApiResponse<UserTemplateEntity> save(@RequestBody Map<String, String> body) {
        return ApiResponse.ok("模板已保存", templateService.save(UserContext.userId(),
                body.get("name"), body.get("columns")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(UserContext.userId(), id);
        return ApiResponse.ok("模板已删除", null);
    }
}
