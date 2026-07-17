package hk.ljx.fishhub.auth.controller;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import hk.ljx.fishhub.auth.alarm.AlarmInterface;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class TestController {

    @NacosValue(value = "${rate-limit.api.limit}", autoRefreshed = true)
    private Integer limit;

    @GetMapping("/test")
    public String test() {
        return "当前限流阈值为: " + limit;
    }

    @Resource
    private AlarmInterface alarm;

    @GetMapping("/alarm")
    public String sendAlarm() {
        alarm.send("系统出错啦，速度上线解决问题！");
        return "alarm success";
    }

}