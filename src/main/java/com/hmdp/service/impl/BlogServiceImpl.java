package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource
    private IFollowService iFollowService;
    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if(blog==null)
            return Result.fail("笔记不存在！");
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        if (redisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, UserHolder.getUser().getId().toString())!=null)
            blog.setIsLike(true);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        if (redisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString())!=null) {
            update().setSql("liked = liked -1").eq("id", id).update();
            redisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id,userId.toString());
        }else {
            update().setSql("liked = liked +1").eq("id", id).update();
            redisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,userId.toString(),System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> range = redisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        List<Long> likes = null;
        if (range != null) {
            likes = range.stream().map(Long::valueOf).collect(Collectors.toList());
        }else
            return Result.ok();
        List<UserDTO> collect = userService.listByIds(likes).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(collect);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean success = save(blog);
        if(!success)
            return Result.fail("新增笔记失败！");
        //查询笔记作者的所有粉丝
        List<Follow> follows = iFollowService.query().eq("follow_user_id", user.getId()).list();
        List<Long> ids = follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        //推送笔记id给所有粉丝
        for(Long id : ids){
            String key=FEED_KEY + id;
            redisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 滚动查询收件箱,redis sortedSet
     * 根据上一次查询的最小时间戳当作range时的max,最小时间戳在上次查询出现的次数作为offset偏移量
     * 实现滚动分页
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.找到收件箱
        Long userId = UserHolder.getUser().getId();
        String key=FEED_KEY + userId;
        //2.查询收件箱
        Set<ZSetOperations.TypedTuple<String>> set = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        //3.非空判断
        if (set == null || set.isEmpty()) {
            return Result.ok();
        }
        //4.处理数据:blogId, 最小时间戳, offset
        List<Long> ids = new ArrayList<>(set.size());
        long minTime =0;
        long os =1;
        for(ZSetOperations.TypedTuple<String> tuple: set){
            String value = tuple.getValue();
            ids.add(Long.valueOf(value));
            long score = tuple.getScore().longValue();
            if(score == minTime)
                os++;
            else{
                minTime = score;
                os = 1;
            }
        }
        //5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset((int) os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
