package hk.ljx.fishhub.search.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.fishhub.search.service.ExtDictService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 向 Elasticsearch IK 插件提供热更新扩展词典。 */
@RestController
@RequestMapping("/search")
public class ExtDictController {

    @Resource
    private ExtDictService extDictService;

    @GetMapping("/ext/dict")
    @ApiOperationLog(description = "获取热更新词典")
    public ResponseEntity<String> extDict() {
        return extDictService.getHotUpdateExtDict();
    }
}
