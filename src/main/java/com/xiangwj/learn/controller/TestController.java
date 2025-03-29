package com.xiangwj.learn.controller;
import com.xiangwj.learn.annotation.RepeatSubmit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @RepeatSubmit(limitType = RepeatSubmit.type.TOKEN, logtime = 10)
    @PostMapping("/saveCountInfo")
    public String saveCountInfo(String accountNo){
        return "test OK";
    }
}