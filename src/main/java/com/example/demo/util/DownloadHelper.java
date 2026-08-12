package com.example.demo.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文件下载响应工具（解决中文文件名导致浏览器文件名解析失败的问题）
 */
public final class DownloadHelper {

    private DownloadHelper() {
    }

    /** 把中文文件名转为安全的ASCII名称 */
    public static String toAscii(String fileName) {
        if (fileName == null || fileName.isBlank()) return "download.bin";
        // 提取扩展名
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            ext = fileName.substring(dot);
            fileName = fileName.substring(0, dot);
        }
        // 过滤非ASCII字符（中文）
        String ascii = fileName.replaceAll("[^A-Za-z0-9_\\-]", "");
        if (ascii.length() < 3) {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            ascii = "TestCase_" + ts;
        }
        return ascii + ext;
    }

    public static String buildContentDisposition(String fileName) {
        String safeName = fileName == null || fileName.isBlank() ? "download.bin" : fileName.trim();
        String asciiName = toAscii(safeName);
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded;
    }

    public static ResponseEntity<byte[]> fileDownload(byte[] data, String fileName, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(fileName));
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
