package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 10L, TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

//    public Shop queryWithPassThrough(Long id){
//        //1.从redis中查找shop
//        String key = CACHE_SHOP_KEY + id;
//        String shopStr = stringRedisTemplate.opsForValue().get(key);
//        //2.redis中存在
//        if (StrUtil.isNotBlank(shopStr)) {
//            //3.将查找到的type返回
//            return JSONUtil.toBean(shopStr,Shop.class);
//        }
//        //3.判断是否为空值
//        if(Objects.equals(shopStr, "")){
//            return null;
//        }
//        //4.redis中不存在：从数据库中查找
//        Shop shop = getById(id);
//        if(shop == null){
//            //4.1若店铺不存在，将该id在redis中存储为空
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //5。将数据存入redis
//        String jsonStr = JSONUtil.toJsonStr(shop);
//        stringRedisTemplate.opsForValue().set(key,jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //6.返回
//        return shop;
//    }
    //获取互斥锁
//    public boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//    //放开互斥锁
//    public void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        String key = CACHE_SHOP_KEY + id;
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //逻辑过期解决缓存击穿
//    public Shop queryWithLogicalExpire(Long id){
//        //1.从redis中查找shop
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.未命中，直接返回null
//        if (StrUtil.isBlank(shopJson)) {
//            return null;
//        }
//        //3.命中，将json转为shop
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //4.判断是否过期
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //4.1未过期：返回
//            return shop;
//        }
//        //4.2已过期
//        // 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean lock = tryLock(lockKey);
//        if(lock){
//            // 开新线程更新缓存
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    this.saveShop2Redis(id, 20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    unlock(lockKey);
//                }
//            });
//        }
//        //4.3返回旧数据
//        return shop;
//    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        //1.更新数据库
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺不存在！");
        }
        updateById(shop);
        String key = CACHE_SHOP_KEY + id;
        //2.删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}
