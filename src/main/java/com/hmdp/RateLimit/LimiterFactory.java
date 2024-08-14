package com.hmdp.RateLimit;

import org.springframework.data.redis.core.StringRedisTemplate;

public class LimiterFactory {
//    private static TokenBucketLimiter tokenBucketLimiter;
//    private static LoginRateLimiter loginRateLimiter;
    public static TokenBucketLimiter getLimiter(int resetBucketInterval, int maxTokens, StringRedisTemplate redisTemplate){
        return new TokenBucketLimiter(resetBucketInterval,maxTokens,redisTemplate);
    }
    public static LoginRateLimiter getLoginLimiter(StringRedisTemplate stringRedisTemplate){
        return new LoginRateLimiter(stringRedisTemplate);
    }
}
