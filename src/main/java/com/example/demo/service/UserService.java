package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.ApiException;
import com.example.demo.entity.UserEntity;
import com.example.demo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserEntity findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
    }

    @Transactional
    public UserEntity register(String username, String password, String nickname) {
        if (username == null || username.trim().length() < 3) {
            throw new ApiException("账号至少 3 个字符");
        }
        if (password == null || password.length() < 6) {
            throw new ApiException("密码至少 6 位");
        }
        if (findByUsername(username.trim()) != null) {
            throw new ApiException("账号已存在，请直接登录");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username.trim());
        user.setPassword(encoder.encode(password));
        user.setNickname((nickname == null || nickname.isBlank()) ? username.trim() : nickname.trim());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public UserEntity login(String username, String password) {
        UserEntity user = findByUsername(username == null ? "" : username.trim());
        if (user == null || !encoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new ApiException(401, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ApiException(403, "账号已停用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    public UserEntity get(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw new ApiException(404, "用户不存在");
        return user;
    }

    public UserEntity updateProfile(Long userId, String nickname, String email) {
        UserEntity user = get(userId);
        if (nickname != null && !nickname.isBlank()) user.setNickname(nickname.trim());
        if (email != null) user.setEmail(email.trim());
        userMapper.updateById(user);
        return user;
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserEntity user = get(userId);
        if (!encoder.matches(oldPassword == null ? "" : oldPassword, user.getPassword())) {
            throw new ApiException("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ApiException("新密码至少 6 位");
        }
        user.setPassword(encoder.encode(newPassword));
        userMapper.updateById(user);
    }
}
