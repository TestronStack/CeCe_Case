package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.CryptoUtil;
import com.example.demo.entity.UserAiConfigEntity;
import com.example.demo.mapper.UserAiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiConfigService {

    public record ModelOption(String id, String name, String provider, String desc) {
    }

    private static final List<ModelOption> MODELS = List.of(
            new ModelOption("deepseek-chat", "DeepSeek Chat", "deepseek", "性价比高，默认推荐"),
            new ModelOption("qwen-turbo", "通义千问 Turbo", "qwen", "响应快，成本低"),
            new ModelOption("qwen-vl-max", "通义千问 Qwen-VL", "qwen", "支持图像理解"),
            new ModelOption("doubao-seed-1-6-vision-250815", "豆包 Vision", "doubao", "视觉理解"),
            new ModelOption("gpt-4o", "OpenAI GPT-4o", "openai", "多模态旗舰"),
            new ModelOption("glm-4v", "智谱 GLM-4V", "glm", "视觉理解")
    );

    private final UserAiConfigMapper configMapper;

    public List<ModelOption> models() {
        return MODELS;
    }

    public Map<String, Object> get(Long userId) {
        UserAiConfigEntity config = configMapper.selectOne(new LambdaQueryWrapper<UserAiConfigEntity>()
                .eq(UserAiConfigEntity::getUserId, userId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", MODELS);
        if (config == null) {
            result.put("provider", "deepseek");
            result.put("modelName", "deepseek-chat");
            result.put("apiKey", "");
            result.put("apiKeyMasked", "");
            result.put("configured", false);
        } else {
            String plain = CryptoUtil.decrypt(config.getApiKey());
            result.put("provider", config.getProvider());
            result.put("modelName", config.getModelName());
            result.put("apiKey", "");
            result.put("apiKeyMasked", CryptoUtil.mask(plain));
            result.put("configured", plain != null && !plain.isEmpty());
        }
        result.put("activeModel", AiService.getCurrentModelName());
        return result;
    }

    public void save(Long userId, String provider, String modelName, String apiKey) {
        if (modelName == null || modelName.isBlank()) modelName = "deepseek-chat";
        if (provider == null || provider.isBlank()) provider = "deepseek";
        UserAiConfigEntity config = configMapper.selectOne(new LambdaQueryWrapper<UserAiConfigEntity>()
                .eq(UserAiConfigEntity::getUserId, userId));
        boolean isNew = config == null;
        if (isNew) config = new UserAiConfigEntity();
        config.setUserId(userId);
        config.setProvider(provider);
        config.setModelName(modelName);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(CryptoUtil.encrypt(apiKey.trim()));
        } else if (isNew) {
            config.setApiKey("");
        }
        config.setUpdatedAt(LocalDateTime.now());
        if (isNew) configMapper.insert(config);
        else configMapper.updateById(config);

        // 立即生效：有 Key 时切换；未配置则保持全局模型
        String plain = CryptoUtil.decrypt(config.getApiKey());
        if (plain != null && !plain.isEmpty()) {
            AiService.switchModel(modelName, plain);
        } else {
            AiService.switchModel(modelName, null);
        }
    }

    /** 任务执行前按用户配置切换模型，返回是否切换成功 */
    public boolean apply(Long userId) {
        UserAiConfigEntity config = configMapper.selectOne(new LambdaQueryWrapper<UserAiConfigEntity>()
                .eq(UserAiConfigEntity::getUserId, userId));
        if (config == null) return false;
        String key = CryptoUtil.decrypt(config.getApiKey());
        return key != null && !key.isEmpty() && AiService.switchModel(config.getModelName(), key);
    }
}
