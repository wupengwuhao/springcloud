package com.wuhao.sc.ribbon.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ComputerService {

    @Autowired
    RestTemplate restTemplate;

    public Integer add(Integer a, Integer b) {
        return restTemplate.getForObject("http://COMPUTE-SERVICE/add?a=" + a + "&b=" + b, Integer.class);
    }

}
