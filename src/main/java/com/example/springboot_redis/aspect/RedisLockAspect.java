package com.example.springboot_redis.aspect;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Description: 分布式锁
 * <p>
 * 先获取锁, 获取不到则继续等待(指定时间), 失败次数(指定)次后跳出, 消费降级(抛出,系统繁忙稍后再试) 如果没有重试次数,方法返回null 记得捕获NP 当重试次数有, 但是重试间隔时间没写, 默认200ms 间隔
 * </p>
 * @author clam
 *
 */
@Aspect
@Component
@Slf4j
@Order(10)
public class RedisLockAspect {

    private static final String LOCK_NAME = "lockName";
    private static final String lOCK_WAIT = "lockWait";
    private static final String AUTO_UNLOCK_TIME = "autoUnlockTime";
    private static final String RETRY_NUM = "retryNum";
    private static final String RETRY_WAIT = "retryWait";

    /**
     * redis工具类
     */
    @Autowired
    private RedissonClient redissonClient;

    @Pointcut("@annotation(com.example.springboot_redis.aspect.RedisLock)")
    public void lockAspect() {
    }

    @Around("lockAspect()")
    public Object lockAroundAction(ProceedingJoinPoint proceeding) throws Throwable {

        // 获取注解中的参数
        Map<String, Object> annotationArgs = this.getAnnotationArgs (proceeding);
        String lockName = (String) annotationArgs.get (LOCK_NAME);
        Assert.notNull (lockName, "分布式,锁名不能为空");
        int retryNum = (int) annotationArgs.get (RETRY_NUM);
        long retryWait = (long) annotationArgs.get (RETRY_WAIT);
        long lockWait = (long) annotationArgs.get (lOCK_WAIT);
        long autoUnlockTime = (long) annotationArgs.get (AUTO_UNLOCK_TIME);

        // 获取锁
        RLock lock = redissonClient.getLock (lockName);
        try {
            boolean res = lock.tryLock (lockWait, autoUnlockTime, TimeUnit.SECONDS);
            if (res) {
                // 执行主逻辑
                return proceeding.proceed ( );

            } else {
                // 如果重试次数为零, 则不重试
                if (retryNum <= 0) {
                    log.info (String.format ("{%s}已经被锁, 不重试", lockName));
                    throw new Exception (String.format ("{%s}已经被锁, 不重试", lockName));
                }

                if (retryWait == 0) {
                    retryWait = 200L;
                }
                // 设置失败次数计数器, 当到达指定次数时, 返回失败
                int failCount = 1;
                while (failCount <= retryNum) {
                    // 等待指定时间ms
                    Thread.sleep (retryWait);
                    if (lock.tryLock (lockWait, autoUnlockTime, TimeUnit.SECONDS)) {
                        // 执行主逻辑
                        return proceeding.proceed ( );
                    } else {
                        log.info (String.format ("{%s}已经被锁, 正在重试[ %s/%s ],重试间隔{%s}毫秒", lockName, failCount, retryNum,
                                retryWait));
                        failCount++;
                    }
                }
                throw new Exception ("系统繁忙, 请稍等再试");
            }
        } catch (Throwable throwable) {
            log.error (String.format ("执行分布式锁发生异常锁名:{%s},异常名称:{%s}", lockName, throwable.getMessage ( )));
            throw throwable;
        } finally {
            lock.unlock ( );
        }
    }

    /**
     * 获取锁参数
     *
     * @param proceeding
     * @return
     */
    private Map<String, Object> getAnnotationArgs(ProceedingJoinPoint proceeding) {
        // if (!(objs[i] instanceof ExtendedServletRequestDataBinder)
        // && !(objs[i] instanceof HttpServletResponseWrapper)) {

        proceeding.getArgs ( );
        Object[] objs = proceeding.getArgs ( );
        String[] argNames = ((MethodSignature) proceeding.getSignature ( )).getParameterNames ( ); // 参数名

        Class target = proceeding.getTarget ( ).getClass ( );
        Method[] methods = target.getMethods ( );
        String methodName = proceeding.getSignature ( ).getName ( );
        for (Method method : methods) {
            if (method.getName ( ).equals (methodName)) {
                Map<String, Object> result = new HashMap<String, Object> ( );
                RedisLock redisLock = method.getAnnotation (RedisLock.class);

                if (StringUtils.isNotBlank (redisLock.lockParameter ( ))) {
                    for (int i = 0; i < objs.length; i++) {
                        if (redisLock.lockParameter ( ).equals (argNames[i])) {
                            result.put (LOCK_NAME, redisLock.lockPrefix ( ) + objs[i]);
                            break;
                        }

                    }
                } else {
                    result.put (LOCK_NAME, redisLock.lockPrefix ( ));
                }
                result.put (lOCK_WAIT, redisLock.lockWait ( ));
                result.put (AUTO_UNLOCK_TIME, redisLock.autoUnlockTime ( ));
                result.put (RETRY_NUM, redisLock.retryNum ( ));
                result.put (RETRY_WAIT, redisLock.retryWait ( ));

                return result;
            }
        }
        throw new RuntimeException ("异常");

    }
}