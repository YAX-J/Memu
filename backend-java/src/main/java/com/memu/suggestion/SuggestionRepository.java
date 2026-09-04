package com.memu.suggestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 建议实例的本地持久化（P1）。
 *
 * 关键判断：建议实例是可再生的 UI 状态（内核 Kùzu 已持久化习惯 + 反馈权重），
 * 这里只存「当前待处理建议 + 已处理动作」，用 JSON 文件落盘即可，
 * 无需引入 H2/SQLite（桌面应用里对可再生的键值列表属过度设计）。
 *
 * 持久化只覆盖 Java 侧职责（建议生命周期 + 用户动作），
 * 不触碰内核的习惯权重 —— 两边职责不重叠。
 */
@Component
public class SuggestionRepository {

    private static final Logger log = LoggerFactory.getLogger(SuggestionRepository.class);

    private final ObjectMapper mapper;
    private final Path file;
    private final Map<String, Suggestion> store = new ConcurrentHashMap<>();

    public SuggestionRepository(
            ObjectMapper mapper,
            @Value("${memu.data-dir:./data}") String dataDir) {
        this.mapper = mapper;
        this.file = Path.of(dataDir, "suggestions.json");
        load();
    }

    public Map<String, Suggestion> all() {
        return store;
    }

    public Suggestion get(String id) {
        return store.get(id);
    }

    public void put(Suggestion s) {
        store.put(s.id(), s);
        save();
    }

    public Suggestion remove(String id) {
        Suggestion s = store.remove(id);
        if (s != null) {
            save();
        }
        return s;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Map<String, Suggestion> data = mapper.readValue(
                    file.toFile(), new TypeReference<Map<String, Suggestion>>() {});
            store.putAll(data);
            log.info("已从 {} 载入 {} 条建议", file, store.size());
        } catch (IOException e) {
            // 文件损坏/格式不符时从空开始，不阻塞启动
            log.warn("读取建议文件失败，从空状态启动: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), store);
        } catch (IOException e) {
            // 写失败不致命（磁盘满/权限），下次写入再试
            log.warn("写入建议文件失败: {}", e.getMessage());
        }
    }
}
