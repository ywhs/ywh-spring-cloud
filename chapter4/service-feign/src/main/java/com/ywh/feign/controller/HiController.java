package com.ywh.feign.controller;

import com.ywh.feign.service.SchedualServiceHi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CreateTime: 2019-03-27 10:03
 * ClassName: HiController
 * Package: com.ywh.feign.controller
 * Describe:
 * 控制层
 *
 * @author YWH
 */
@RestController
public class HiController {

    @Autowired
    private SchedualServiceHi schedualServiceHi;


    @GetMapping(value = "/hi")
    public String sayHi(@RequestParam String name) {
        return schedualServiceHi.sayHiFromClientOne(name);
    }

}
