package com.hmdp.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    //自定义消息转换器为json
    @Bean
    public MessageConverter jacksonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
