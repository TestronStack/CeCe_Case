package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF文档解析器
 * 职责：读取PDF文档，按真实页码提取内容
 */
@Slf4j
public class PdfParser {

    /**
     * 读取PDF全文（不分页）
     */
    public static String readPdf(String filePath) {
        log.info("正在读取PDF文档: {}", filePath);
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("PDF读取完成，共 {} 页，字符数: {}", document.getNumberOfPages(), text.length());
            return text;
        } catch (IOException e) {
            log.error("读取PDF失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 读取PDF文档（按页码范围）
     * PDF的页码是真实的物理页码，完全精确
     *
     * @param filePath  文件路径
     * @param startPage 起始页码（从1开始）
     * @param endPage   结束页码（0表示读到最后一页）
     */
    public static String readPdf(String filePath, int startPage, int endPage) {
        log.info("正在读取PDF文档: {}，页码范围: {} - {}", filePath,
                startPage > 0 ? startPage : "开始", endPage > 0 ? endPage : "结束");

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            int totalPages = document.getNumberOfPages();
            int actualStart = Math.max(1, Math.min(startPage > 0 ? startPage : 1, totalPages));
            int actualEnd = endPage > 0 ? Math.min(endPage, totalPages) : totalPages;

            log.info("PDF共 {} 页，实际读取范围: {} - {}（精确页码）", totalPages, actualStart, actualEnd);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(actualStart);
            stripper.setEndPage(actualEnd);
            // 保留段落间的换行

            String text = stripper.getText(document);
            log.info("PDF读取完成，字符数: {}", text.length());
            return text;

        } catch (IOException e) {
            log.error("读取PDF失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 从PDF/文本中提取接口定义（按方法+路径模式匹配）
     * 兼容 Markdown 装饰：代码块反引号（`POST /api`）、标题（### POST /api）、加粗（**GET** /api）、列表（- GET /api）等
     */
    public static List<String> extractInterfaces(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();

        List<String> interfaces = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentApi = new StringBuilder();

        // 匹配 HTTP 方法 + 路径（路径需包含 /，避免误匹配 "POST 描述" 这类文本）
        // 方法名允许被 markdown 加粗星号包裹（如 **DELETE** /api/x），方法前的标题/反引号/列表等装饰不影响匹配
        java.util.regex.Pattern methodPattern =
                java.util.regex.Pattern.compile("\\b\\*{0,2}(GET|POST|PUT|DELETE|PATCH)\\*{0,2}\\s+([^\\s`,;.()（）]+)");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 跳过 curl 命令行示例（如 curl -X DELETE http://...），避免误当接口，也不打断当前接口块的描述
            if (trimmed.matches("(?i).*\\bcurl\\b.*")) continue;

            java.util.regex.Matcher m = methodPattern.matcher(trimmed);
            if (m.find() && m.group(2).contains("/")) {
                if (currentApi.length() > 0) {
                    interfaces.add(currentApi.toString());
                }
                // 重建干净的接口头 "METHOD /path"，并把同一行方法/路径之后的描述（如 "### POST /api/login 获取Token"）保留
                String header = m.group(1) + " " + m.group(2);
                String rest = trimmed.substring(m.end()).replaceAll("^[`*\\s]+|[`*\\s]+$", "");
                currentApi = new StringBuilder(header);
                if (!rest.isEmpty()) {
                    currentApi.append(" ").append(rest);
                }
            } else {
                currentApi.append("\n").append(trimmed);
            }
        }
        if (currentApi.length() > 0) {
            interfaces.add(currentApi.toString());
        }

        // 过滤和去重：同一接口在文档多处出现（定义/目录/调用顺序表）只保留一次，按「方法+规范化路径」去重
        interfaces.removeIf(api -> api.length() < 5 || !api.contains("/"));
        return dedupeInterfaces(interfaces);
    }

    /**
     * 接口块去重：按「方法 + 规范化路径」去重，同一接口在文档中重复出现（接口定义 / 调用顺序表 / 快速验证等）只保留第一次出现的完整块。
     * 同时过滤 curl 命令行示例（如 "curl -X DELETE http://host/api/user/2"），避免把示例命令当成接口定义。
     *
     * @param interfaces 提取到的接口块列表（每块首行为 方法 路径，后续为描述）
     * @return 去重后的接口块，保持原有顺序
     */
    public static List<String> dedupeInterfaces(List<String> interfaces) {
        if (interfaces == null || interfaces.isEmpty()) return interfaces;

        java.util.regex.Pattern methodPattern =
                java.util.regex.Pattern.compile("\\b\\*{0,2}(GET|POST|PUT|DELETE|PATCH)\\*{0,2}\\s+([^\\s`,;.()（）]+)");

        java.util.LinkedHashMap<String, String> unique = new java.util.LinkedHashMap<>();
        for (String block : interfaces) {
            if (block == null) continue;
            String firstLine = block.trim().split("\n")[0].trim();
            // 过滤 curl 命令行示例（不当作接口定义）
            if (firstLine.matches("(?i).*\\bcurl\\b.*")) continue;
            java.util.regex.Matcher m = methodPattern.matcher(firstLine);
            if (!(m.find() && m.group(2).contains("/"))) continue;
            String norm = normalizeInterfacePath(m.group(2));
            if (norm.isEmpty()) continue;
            String key = m.group(1).toUpperCase() + " " + norm;
            unique.putIfAbsent(key, block);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 规范化接口路径：去 http(s)://host[:port] 前缀、去查询参数(?xxx)、去尾部标点/续行符，
     * 并把路径参数统一成占位符，使同一接口的模板路径与具体示例路径视为同一接口：
     * - /api/user/2 与 /api/user/{id} 都归为 /api/user/{}
     * - /api/user?page=1 与 /api/user 都归为 /api/user
     */
    private static String normalizeInterfacePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        p = p.replaceFirst("(?i)^https?://[^/]+", "");
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        // 先统一路径参数（保留花括号，避免去尾标点时破坏 {id} 的右括号）
        p = p.replaceAll("/\\{[^}]*\\}", "/{}");   // {id}/{username} → /{}
        p = p.replaceAll("/\\d+(?=/|$)", "/{}");   // 具体数字段 /2 → /{}
        // 再去尾部标点/续行符（如 "，。）、\\" 等）
        p = p.replaceAll("[\\\\,;.。，;）)\\]}\\s]+$", "");
        return p;
    }

    /**
     * 获取PDF总页数
     */
    public static int getPageCount(String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            log.error("获取PDF页数失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
