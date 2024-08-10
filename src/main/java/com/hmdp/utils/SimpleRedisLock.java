package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    @Override
    public boolean tryLock(long timeOutSec) {
        //获取线程id
        String name1 = Thread.currentThread().getName();
        String key = KEY_PREFIX + name;
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, name1, timeOutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        String key = KEY_PREFIX + name;
        //由于可能存在分布式锁误删问题，即该线程阻塞导致锁过期同时有别的线程获取锁，而此线程苏醒后将别的线程的锁删除
        //在删除锁之前判断是不是自己获取的锁
        if (Objects.equals(stringRedisTemplate.opsForValue().get(key), Thread.currentThread().getName())) {
            stringRedisTemplate.delete(key);
        }
    }
}
