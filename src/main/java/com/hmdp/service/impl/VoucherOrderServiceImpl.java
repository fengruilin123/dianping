package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.RateLimit.LimiterFactory;
import com.hmdp.RateLimit.TokenBucketLimiter;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_LIMIT_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource(name = "ConfirmRT")
    private RabbitTemplate rabbitTemplate;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 将验证用户购买资格（一人一票、库存是否充足）交给响应更快的redis完成，直接在redis中扣减库存并将购买用户的id记录
     * 此redis操作为了保证原子性交由lua脚本完成，将订单信息保存到消息队列中，使用别的线程异步写入数据库
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        //从令牌桶获取令牌
        TokenBucketLimiter tokenBucketLimiter = LimiterFactory.getLimiter(1000, 10, stringRedisTemplate);
        boolean access = tokenBucketLimiter.access(SECKILL_LIMIT_KEY + voucherId);
        if(!access){
            return Result.fail("您已被限流！");
        }
        //1.执行lua脚本
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString());
        //2.判断结果为0
        assert execute != null;
        if(execute.intValue() != 0){
            return Result.fail(execute == 1? "已购买！" : "库存不足！");
        }
        //3.生成订单id
        long oderId = redisIdWorker.nextId("oder");
        Long userId = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(oderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        CorrelationData correlationData = new CorrelationData(String.valueOf(oderId));
        rabbitTemplate.convertAndSend("voucher.direct", "voucherOder", voucherOrder, correlationData);
        //4.返回订单编号
        return Result.ok(oderId);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始/结束
//        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())){
//            return Result.fail("秒杀未开始！");
//        }
//        if(LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
//            return Result.fail("秒杀已结束！");
//        }
//        //3.判断库存是否充足
//        Integer stock = seckillVoucher.getStock();
//        if(stock < 1){
//            return Result.fail("已抢光！");
//        }
//        //当一个用户进来后，获取用户id并转换为字符串添加到字符串池中获取引用，这样做到同一id引用唯一，对这个字符串加锁，保证同一个id只有一个线程可以访问createVoucherOder
//        Long userId = UserHolder.getUser().getId();
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "oder:" + userId);
//        RLock lock = redissonClient.getLock("lock:oder:" + userId);
//        if(!lock.tryLock()){
//            return Result.fail("订单正在处理！");
//        }
//        try {
//            //生成IVoucherService代理对象(事务)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//
//    }
//
//    @Transactional
//    public Result createVoucherOder(Long voucherId) {
//        //一人一单
//        Long userId = UserHolder.getUser().getId();
//        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//        if (count > 0) {
//            return Result.fail("你已抢购！");
//        }
//        //4.扣减库存，创建订单
//        //乐观锁CAS机制,where id = ? and stock > 0 解决线程安全超卖问题
//        boolean success = seckillVoucherService.update().setSql("stock = stock -1").eq("voucher_id", voucherId).gt("stock", 0).update();
//        if (!success) {
//            return Result.fail("已抢光！");

//        }
//        long oderId = redisIdWorker.nextId(SECKILL_STOCK_KEY);
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(oderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        return Result.ok(oderId);
//    }
}
