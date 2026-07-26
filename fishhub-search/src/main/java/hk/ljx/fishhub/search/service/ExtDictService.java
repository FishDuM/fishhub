package hk.ljx.fishhub.search.service;

import org.springframework.http.ResponseEntity;

/** Elasticsearch IK 插件热更新词典服务。 */
public interface ExtDictService {

    ResponseEntity<String> getHotUpdateExtDict();
}
