package com.ywh.feign.service.impl;

import com.ywh.feign.service.SchedualServiceHi;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * CreateTime: 2019-03-27 11:42
 * ClassName: SchedualServiceHiHystric
 * Package: com.ywh.feign.service.impl
 * Describe:
 * 当调用的服务崩溃时，需要转到本类中执行相应方法
 *
 * @author YWH
 */
@Component
public class SchedualServiceHiHystric implements SchedualServiceHi{

    @Override
    public String sayHiFromClientOne(String name) {
        return "hi, " + name + "sorry, error fegin";
    }
}
