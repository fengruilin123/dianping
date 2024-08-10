package com.hmdp.RateLimit;

import org.springframework.data.redis.core.StringRedisTemplate;

public class LimiterFactory {
    static TokenBucketLimiter tokenBucketLimiter;
    public static TokenBucketLimiter getLimiter(int resetBucketInterval, int maxTokens, StringRedisTemplate redisTemplate){
        return new TokenBucketLimiter(resetBucketInterval,maxTokens,redisTemplate);
    }
}
