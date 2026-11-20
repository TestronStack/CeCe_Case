package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/ai")
@RequiredArgsConstructor
public class SettingController {

    private final AiConfigService aiConfigService;

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(aiConfigService.get(UserContext.userId()));
    }

    @PutMapping
    public ApiResponse<Void> save(@RequestBody Map<String, String> body) {
        aiConfigService.save(UserContext.userId(), body.get("provider"),
                body.get("modelName"), body.get("apiKey"));
        return ApiResponse.ok("模型配置已保存", null);
    }
}
