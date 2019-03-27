package com.ywh.feign.service;

import com.ywh.feign.service.impl.SchedualServiceHiHystric;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CreateTime: 2019-03-27 9:57
 * ClassName: SchedualServiceHi
 * Package: com.ywh.feign.service
 * Describe:
 * 伪Http客户端，调用其他微服务的接口（controller）
 *  FeignClient(value = "eureka-hi") 表明我是一个Feign的客户端，调用的是eureka-hi这个微服务
 * @author YWH
 */
@FeignClient(value = "eureka-hi",fallback = SchedualServiceHiHystric.class)
public interface SchedualServiceHi {

    /**
     *  间接性的充当一个controller，但不是本项目的，而是代替了@FeignClient(value = "eureka-hi")项目中的hi接口
     * @param name 参数名字
     * @return 返回字符串
     */
    @RequestMapping(value = "/hi",method = RequestMethod.GET)
    String sayHiFromClientOne(@RequestParam(value = "name") String name);

}
