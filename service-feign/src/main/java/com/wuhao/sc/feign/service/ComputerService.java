package com.wuhao.sc.feign.service;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "compute-service", fallback = ComputerService.ComputerServiceHystric.class)
public interface  ComputerService {

    @RequestMapping(value = "/add" ,method = RequestMethod.GET)
    Integer add(@RequestParam("a") Integer a, @RequestParam("b") Integer b);

    @Component
    public static class ComputerServiceHystric implements ComputerService {
        @Override
        public Integer add(Integer a, Integer b) {
            return 0;
        }
    }

}
