package hk.ljx.fishhub.search.service.impl;

import hk.ljx.fishhub.search.config.ElasticsearchProperties;
import hk.ljx.fishhub.search.service.ExtDictService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class ExtDictServiceImpl implements ExtDictService {

    @Resource
    private ElasticsearchProperties elasticsearchProperties;

    @Override
    public ResponseEntity<String> getHotUpdateExtDict() {
        String dictPath = elasticsearchProperties.getHotUpdateExtDict();
        if (dictPath == null || dictPath.isBlank()) {
            log.warn("未配置 Elasticsearch 热更新词典路径");
            return ResponseEntity.notFound().build();
        }

        try {
            Path path = Path.of(dictPath);
            if (!Files.isRegularFile(path)) {
                log.warn("Elasticsearch 热更新词典不存在: {}", path);
                return ResponseEntity.notFound().build();
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setETag('"' + Integer.toHexString(content.hashCode()) + '"');
            headers.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));

            return ResponseEntity.ok()
                    .headers(headers)
                    .lastModified(Files.getLastModifiedTime(path).toMillis())
                    .body(content);
        } catch (IOException e) {
            log.error("读取 Elasticsearch 热更新词典失败: {}", dictPath, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
