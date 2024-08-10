package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hmdp.RateLimit.LimiterFactory;
import com.hmdp.RateLimit.TokenBucketLimiter;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class DianPingApplicationTests {
    @Resource
    public ShopServiceImpl shopService;
    @Resource
    public RedisIdWorker redisIdWorker;
    @Resource
    public IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Test
    public void testRedis() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);
    }

    @Test
    public void testId(){
        for(int i = 0;i<100;i++){
            long id = redisIdWorker.nextId("voucher");
            System.out.println(id);
        }
    }
    @Test
    public void tokenTxt() throws IOException {
        List<User> userList = userService.list();
        for (User user : userList) {
            String token = UUID.randomUUID().toString(true);
            //将User转为Hash存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                    .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
//            stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.SECONDS);
        }
        Set<String> keys = stringRedisTemplate.keys(LOGIN_USER_KEY + "*");
        FileWriter fiLeWriter = new FileWriter(System. getProperty("user.dir") + "\\tokens.txt");
        BufferedWriter bufferedWriter = new BufferedWriter(fiLeWriter);
        assert keys != null;
        for(String key : keys){
            String token = key.substring(LOGIN_USER_KEY.length());
            bufferedWriter.write(token + "\n");
        }
    }
    @Test
    public void saveShop(){
        List<Shop> shopList = shopService.list();
        for(Shop shop:shopList){
            String key = CACHE_SHOP_KEY + shop.getId();
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(1000));
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        }
    }
    @Test
    public void BFTest(){
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("Shop:BloomFilter");
        bloomFilter.tryInit(100L,0.05);
        bloomFilter.add("Shop:1");
        bloomFilter.add("Shop:2");
        bloomFilter.add("Shop:200");
    }
    @Test
    public void BFTest1(){
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("BloomFilter:Shop");
        boolean contains = bloomFilter.contains(CACHE_SHOP_KEY+1L);
        System.out.println("1存在？"+contains);
        boolean contains1 = bloomFilter.contains("Shop:2");
        System.out.println("2存在？"+contains1);
    }
    @Test
    public void BFPreheat(){
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("BloomFilter:Shop");
        bloomFilter.tryInit(100L,0.05);
        List<Shop> list = shopService.list();
        for (Shop shop : list) {
            bloomFilter.add(CACHE_SHOP_KEY+shop.getId());
        }
    }
    @Test
    public void limitTest(){
        TokenBucketLimiter tokenBucketLimiter = LimiterFactory.getLimiter(1000000, 10, stringRedisTemplate);
        System.out.println(tokenBucketLimiter.access("testLimit"));
    }
}
