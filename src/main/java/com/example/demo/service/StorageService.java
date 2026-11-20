package com.example.demo.service;

import com.example.demo.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

/** 本地文件存储：data/users/{userId}/documents|files */
@Service
public class StorageService {

    private final Path root;

    public StorageService(@Value("${cece.storage.root:./data}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储目录: " + this.root, e);
        }
    }

    public Path userDir(Long userId, String sub) {
        Path dir = root.resolve("users").resolve(String.valueOf(userId)).resolve(sub);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(500, "创建目录失败: " + e.getMessage());
        }
        return dir;
    }

    /** 保存上传文件，返回绝对路径 */
    public Path saveUpload(MultipartFile file, Long userId, String originalName) {
        String safeName = originalName == null ? "upload" : originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path target = userDir(userId, "documents").resolve(System.currentTimeMillis() + "_" + safeName);
        try {
            // 使用流拷贝而非 transferTo：Undertow 请求结束后会清理临时目录，transferTo 可能找不到临时文件
            Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "文件保存失败: " + e.getMessage());
        }
        return target;
    }

    public Path saveBytes(Long userId, String sub, String fileName, byte[] data) {
        Path target = userDir(userId, sub).resolve(fileName);
        try {
            Files.write(target, data);
        } catch (IOException e) {
            throw new ApiException(500, "文件写入失败: " + e.getMessage());
        }
        return target;
    }

    public Path resolve(String storedPath) {
        Path p = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!p.startsWith(root)) throw new ApiException(403, "非法文件路径");
        return p;
    }

    public void deleteQuietly(String storedPath) {
        try {
            if (storedPath != null) Files.deleteIfExists(Paths.get(storedPath));
        } catch (Exception ignored) {
        }
    }

    public static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(Files.readAllBytes(path));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
