package com.hmdp.RateLimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 令牌桶算法限流
 */
public class TokenBucketLimiter {
    //重置桶内令牌的时间间隔
    private final long resetBucketInterval;

    //桶内最大令牌数
    private final long maxTokens;

    //添加令牌时间间隔
    private final long intervalPerTokens;

    //Redis代理
    private StringRedisTemplate redisTemplate;

    // 转成 redis 的脚本
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT;
    static {
        LIMIT_SCRIPT = new DefaultRedisScript<>();
        LIMIT_SCRIPT.setLocation(new ClassPathResource("limit.lua"));
        LIMIT_SCRIPT.setResultType(Long.class);
    }

    //构造函数
    public TokenBucketLimiter(long resetBucketInterval, long maxTokens, StringRedisTemplate redisTemplate) {
        this.resetBucketInterval = resetBucketInterval;
        this.maxTokens = maxTokens;
        this.redisTemplate=redisTemplate;
        this.intervalPerTokens = resetBucketInterval / maxTokens;
    }

    public boolean access(String key){
        List<String> list = new ArrayList<>();
        list.add(String.valueOf(maxTokens));
        list.add(String.valueOf(intervalPerTokens));
        list.add(String.valueOf(resetBucketInterval));
        Long execute = redisTemplate.execute(LIMIT_SCRIPT, Collections.singletonList(key), list.toArray(new String[]{}));
        assert execute!=null;
        return execute > 0;
    }
}
