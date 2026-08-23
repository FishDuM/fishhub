package hk.ljx.fishhub.distributed.id.generator.biz.controller;

import hk.ljx.fishhub.distributed.id.generator.biz.core.common.Result;
import hk.ljx.fishhub.distributed.id.generator.biz.core.common.Status;
import hk.ljx.fishhub.distributed.id.generator.biz.exception.LeafServerException;
import hk.ljx.fishhub.distributed.id.generator.biz.service.SnowflakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/id")
@Slf4j
@RequiredArgsConstructor
public class LeafController {

    private final SnowflakeService snowflakeService;

    @GetMapping(value = "/snowflake/get/{key}")
    public String getSnowflakeId(@PathVariable("key") String key) {
        Result result = snowflakeService.getId(key);
        if (result.getStatus().equals(Status.EXCEPTION)) {
            throw new LeafServerException(result.toString());
        }
        return String.valueOf(result.getId());
    }
}
