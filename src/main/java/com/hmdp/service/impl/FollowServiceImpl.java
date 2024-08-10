package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.USER_FOLLOW;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    @Override
    public Result follow(Long followId, boolean isFollow) {
        Long UserId = UserHolder.getUser().getId();
        if(isFollow){
            stringRedisTemplate.opsForSet().add(USER_FOLLOW+ UserId,followId.toString());
            Follow follow = new Follow();
            follow.setUserId(UserId);
            follow.setFollowUserId(followId);
            save(follow);
        }else {
            stringRedisTemplate.opsForSet().remove(USER_FOLLOW+ UserId,followId.toString());
            remove(new QueryWrapper<Follow>().eq("user_id",UserId).eq("follow_user_id",followId));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followId) {
        Long UserId = UserHolder.getUser().getId();
//        Integer count = query().eq("user_id", UserId).eq("follow_user_id", followId).count();
        Boolean isFollow = stringRedisTemplate.opsForSet().isMember(USER_FOLLOW + UserId, followId.toString());
        return Result.ok(isFollow);
    }

    @Override
    public Result commonFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(USER_FOLLOW + userId, USER_FOLLOW + followId);
        List<Long> ids = null;
        if (intersect == null||intersect.isEmpty()) {
            return Result.ok();
        }
        ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
