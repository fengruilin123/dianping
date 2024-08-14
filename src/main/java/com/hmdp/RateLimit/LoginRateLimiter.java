package com.hmdp.RateLimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Set;

@Service
public class LoginRateLimiter {

    private static final String LOGIN_RATE_LIMIT_KEY_PREFIX = "login_rate_limit:";
    private StringRedisTemplate stringRedisTemplate;
    private final long windowInSeconds = 300L;
    private final int maxAttempts = 3;

    public LoginRateLimiter(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    public boolean isLoginAllowed(String phone) {
        ZSetOperations<String, String> ops = stringRedisTemplate.opsForZSet();
        String key = LOGIN_RATE_LIMIT_KEY_PREFIX + phone;
        double now = Instant.now().getEpochSecond();
        // 移除过期的登录记录
        ops.removeRangeByScore(key, 0, now - windowInSeconds);

        // 获取当前时间窗口内的登录次数
        Set<String> attempts = ops.rangeByScore(key, now - windowInSeconds, now);
        long count = attempts.stream().count();

        // 判断是否超过最大尝试次数
        if(count >= maxAttempts){
            return false;
        }
        // 尝试添加当前登录记录
        ops.add(key, String.valueOf(now), now);
        return true;
    }
}
