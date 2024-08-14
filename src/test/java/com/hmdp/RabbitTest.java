package com.hmdp;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class RabbitTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource(name = "ConfirmRT")
    private RabbitTemplate rabbitTemplate;

    @Test
    public void rabbitTest(){
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(123456L);
        voucherOrder.setUserId(111L);
        voucherOrder.setVoucherId(100L);
        CorrelationData correlationData = new CorrelationData(String.valueOf(123456L));
        rabbitTemplate.convertAndSend("voucher.direct", "voucherOder", voucherOrder, correlationData);
        rabbitTemplate.convertAndSend("voucher.direct", "voucherOder", voucherOrder, correlationData);
    }
}
