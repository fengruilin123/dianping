package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followId, boolean isFollow);

    Result isFollow(Long followId);

    Result commonFollow(Long followId);
}
