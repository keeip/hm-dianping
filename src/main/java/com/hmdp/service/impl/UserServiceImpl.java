package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号是否正确
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码格式错误");
        }
        //生成六位验证码
        String code= RandomUtil.randomNumbers(6);
        //存入redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("发送验证码成功,验证码为{}",code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //验证手机号格式
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        //手机号格式正确，从session取出相应的验证码判断
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
        String code=loginForm.getCode();
        if (cacheCode==null||!cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        //验证码验证成功，判断用户是否存在，存在返回用
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user==null){
            user=createUserWithPhone(phone);
        }
        //生成token
        String token= UUID.randomUUID().toString(true);

        UserDTO userDTO= BeanUtil.copyProperties(user,UserDTO.class);
        //插入要求value为string类型

        Map<String,Object> map=BeanUtil.beanToMap(userDTO);
        map.forEach((key,value)->{
            if (value!=null){
                map.put(key,value.toString());
            }
        });
        String tokenKey=LOGIN_USER_KEY+token;

        stringRedisTemplate.opsForHash().putAll(tokenKey,map);
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(5));
        save(user);
        return user;
    }
}
