package com.example.springboot_redis.aspect;

import org.springframework.core.annotation.Order;

import java.lang.annotation.*;

/**
 * redis锁注解
 *
 * @author clam
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
@Order(value = 10)
public @interface RedisLock {
    // 锁前缀
    String  lockPrefix() default "";

    // 方法参数名（用于取参数名的值与锁前缀拼接成锁名），尽量不要用对象map等，对象会toString后与锁前缀拼接
    String lockParameter() default "";

    // 尝试加锁，最多等待时间（毫秒）
    long lockWait() default 3000L;

    // 自动解锁时间 （毫秒）
    long autoUnlockTime() default 10000L;

    // 重试次数
    int retryNum() default 0;

    // 重试等待时间 （毫秒）
    long retryWait() default 500L;

}


