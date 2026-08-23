package hk.ljx.fishhub.search.biz.service.impl;

import hk.ljx.fishhub.search.biz.service.ExtDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@Service
@Slf4j
public class ExtDictServiceImpl implements ExtDictService {

    /**
     * 热更新词典路径
     */
    @Value("${elasticsearch.hotUpdateExtDict}")
    private String hotUpdateExtDict;

    /**
     * 获取热更新词典
     *
     * @return
     */
    @Override
    public ResponseEntity<String> getHotUpdateExtDict() {
        try {
            if (hotUpdateExtDict.startsWith("classpath:")) {
                ClassPathResource resource = new ClassPathResource(hotUpdateExtDict.substring("classpath:".length()));
                if (!resource.exists()) {
                    return ResponseEntity.notFound().build();
                }
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] bytes = inputStream.readAllBytes();
                    String eTag = DigestUtils.md5DigestAsHex(bytes);
                    String fileContent = new String(bytes, StandardCharsets.UTF_8);
                    return ResponseEntity.ok()
                            .eTag(eTag)
                            .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                            .body(fileContent);
                }
            }
            // 获取文件的最后修改时间
            Path path = Paths.get(hotUpdateExtDict);
            long lastModifiedTime = Files.getLastModifiedTime(path).toMillis();

            // 生成 ETag（使用文件内容的 MD5 哈希值）
            byte[] bytes = Files.readAllBytes(path);
            String eTag = DigestUtils.md5DigestAsHex(bytes);
            String fileContent = new String(bytes, StandardCharsets.UTF_8);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", eTag);

            // 设置内容类型为 UTF-8
            headers.setContentType(MediaType.valueOf("text/plain;charset=UTF-8"));

            // 返回文件内容和 HTTP 头部
            return ResponseEntity.ok()
                    .headers(headers)
                    .lastModified(lastModifiedTime) // 请求头中设置 Last-Modified
                    .body(fileContent);
        } catch (java.nio.file.NoSuchFileException e) {
            log.warn("==> 热更新词典不存在: {}", hotUpdateExtDict);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("==> 获取热更新词典异常: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
