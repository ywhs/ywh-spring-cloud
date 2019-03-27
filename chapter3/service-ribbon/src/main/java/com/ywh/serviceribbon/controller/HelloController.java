package com.ywh.serviceribbon.controller;

import com.ywh.serviceribbon.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CreateTime: 2019-03-26 17:47
 * ClassName: HelloController
 * Package: com.ywh.serviceribbon.controller
 * Describe:
 * 控制器
 *
 * @author YWH
 */
@RestController
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping(value = "hi")
    public String hi(@RequestParam String name){
        return helloService.hiService(name);
    }

}
