package com.hmdp.Listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class Listener {
    @Autowired
    private IVoucherOrderService iVoucherOrderService;
    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "voucher.queue"),
            exchange = @Exchange(value = "voucher.direct"),
            key = "voucherOder"
    ))
    public void voucherOderListener(VoucherOrder voucherOder){ //listener接受的数据类型和发送的数据类型保持一致
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent("sk:vo:" + voucherOder.getId().toString(), "1");
        if(Boolean.FALSE.equals(flag)){
            log.info("消息："+voucherOder.getId().toString()+"已消费过！");
            return;
        }
        stringRedisTemplate.expire("sk:vo:" + voucherOder.getId().toString(),30, TimeUnit.MINUTES);
        iVoucherOrderService.save(voucherOder);
        iSeckillVoucherService.update().setSql("stock = stock -1").eq("voucher_id", voucherOder.getVoucherId()).gt("stock", 0).update();
    }
}
