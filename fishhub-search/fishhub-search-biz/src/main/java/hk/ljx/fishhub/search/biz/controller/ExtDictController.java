package hk.ljx.fishhub.search.biz.controller;

import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.fishhub.search.biz.service.ExtDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/search")
@Slf4j
@RequiredArgsConstructor
public class ExtDictController {

    private final ExtDictService extDictService;

    @GetMapping("/ext/dict")
    @ApiOperationLog(description = "热更新词典")
    public ResponseEntity<String> extDict() {
        return extDictService.getHotUpdateExtDict();
    }

}
