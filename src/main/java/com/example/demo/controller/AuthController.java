package com.example.demo.controller;

import com.example.demo.auth.JwtUtil;
import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.UserEntity;
import com.example.demo.service.ProjectService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final ProjectService projectService;
    private final JwtUtil jwtUtil;

    public record LoginRequest(String username, String password, String nickname) {
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody LoginRequest req) {
        UserEntity user = userService.register(req.username(), req.password(), req.nickname());
        projectService.ensureDefault(user.getId());
        return ApiResponse.ok("注册成功", tokenPayload(user));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req) {
        UserEntity user = userService.login(req.username(), req.password());
        projectService.ensureDefault(user.getId());
        return ApiResponse.ok("登录成功", tokenPayload(user));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        UserEntity user = userService.get(UserContext.userId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", toView(user));
        return ApiResponse.ok(data);
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        UserEntity user = userService.updateProfile(UserContext.userId(),
                body.get("nickname"), body.get("email"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", toView(user));
        return ApiResponse.ok("资料已更新", data);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        userService.changePassword(UserContext.userId(),
                body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.ok("密码已修改", null);
    }

    private Map<String, Object> tokenPayload(UserEntity user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtUtil.generate(user.getId(), user.getUsername()));
        data.put("user", toView(user));
        return data;
    }

    private Map<String, Object> toView(UserEntity user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("nickname", user.getNickname());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        view.put("createdAt", user.getCreatedAt());
        return view;
    }
}
