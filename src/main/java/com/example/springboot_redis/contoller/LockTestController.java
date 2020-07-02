package com.example.springboot_redis.contoller;

import com.example.springboot_redis.aspect.RedisLock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分布式Redis锁测试controller
 *
 */
@RestController
@RequestMapping("/lock")
public class LockTestController {


    private int  i=1000;
    // 测试分布式锁使用jmeter并发调用接口测试
    @GetMapping("/testLock1")
    @RedisLock(lockPrefix ="HANDLE", lockParameter = "prouctcount")
    public void handle(int prouctcount) {
        i=i-prouctcount;
        System.out.println ( "i减少到"+i);
    }

}
