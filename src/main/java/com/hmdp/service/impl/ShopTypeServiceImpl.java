package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.injector.methods.SelectList;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryTypeList() {
        //1.从redis中查找shopType
        String typeListStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        //2.redis中存在
        if (StrUtil.isNotBlank(typeListStr)) {
            //3.将查找到的type返回
            return JSONUtil.toList(typeListStr, ShopType.class);
        }
        //4.redis中不存在：从数据库中查找
        List<ShopType> typeList = list();
        //5。将数据存入redis
        String jsonStr = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,jsonStr);
        //6.返回
        return typeList;
    }
}
