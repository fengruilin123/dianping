package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

//    public <R, ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit){
//        //1.从redis中查找shop
//        String key = prefix + id;
//        String json = stringRedisTemplate.opsForValue().get(key);
//        //2.redis中存在
//        if (StrUtil.isNotBlank(json)) {
//            //3.返回
//            return JSONUtil.toBean(json,type);
//        }
//        //3.判断是否为空值
//        if(Objects.equals(json, "")){
//            return null;
//        }
//        //4.redis中不存在：从数据库中查找
//        R r = dbCallBack.apply(id);
//        if(r == null){
//            //4.1若店铺不存在，将该id在redis中存储为空
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //5。将数据存入redis
//       this.set(key, r, time, unit);
//        //6.返回
//        return r;
//    }

    /**
     * 布隆过滤器解决缓存穿透
     */
    public <R, ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit){
        //获取布隆过滤器
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(SHOP_BLOOMFILTER);
        //检查布隆过滤器中是否有该店铺
        String key = prefix + id;
        boolean contains = bloomFilter.contains(key);
        if(!contains){
            log.info("布隆过滤器：店铺id{}不存在！",id);
            return null;
        }
        //1.从redis中查找shop
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.redis中存在
        if (StrUtil.isNotBlank(json)) {
            //3.返回
            return JSONUtil.toBean(json,type);
        }
        //3.判断是否为空值
        if(Objects.equals(json, "")){
            return null;
        }
        //4.redis中不存在：从数据库中查找
        R r = dbCallBack.apply(id);
        if(r == null){
            //4.1若店铺不存在，将该id在redis中存储为空
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5。将数据存入redis
        this.set(key, r, time, unit);
        //6.返回
        return r;
    }
    public <R, ID> R  queryWithLogicalExpire(String prefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit){
        //1.从redis中查找shop
        String key = prefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.未命中，直接返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //3.命中，将json转为shop
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //4.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1未过期：返回
            return r;
        }
        //4.2已过期
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if(lock){
            // 开新线程更新缓存
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.setWithLogicalExpire(key, dbCallBack.apply(id), time, unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        //4.3返回旧数据
        return r;
    }

    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //放开互斥锁
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
