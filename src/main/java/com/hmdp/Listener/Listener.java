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
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Listener {
    @Autowired
    private IVoucherOrderService iVoucherOrderService;
    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "voucher.queue"),
            exchange = @Exchange(value = "voucher.direct"),
            key = "voucherOder"
    ))
    public void voucherOderListener(VoucherOrder voucherOder){ //listener接受的数据类型和发送的数据类型保持一致
        iVoucherOrderService.save(voucherOder);
        iSeckillVoucherService.update().setSql("stock = stock -1").eq("voucher_id", voucherOder.getVoucherId()).gt("stock", 0).update();
    }
}
