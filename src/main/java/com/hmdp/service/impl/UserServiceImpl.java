package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String s = RandomUtil.randomNumbers(6);

//        //保存验证码到session
//        session.setAttribute("code", s); // 保存验证码到session
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, s, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送验证码成功，验证码：{}", s);
        return Result.ok();
    }


    /**
     * 用户登录
     * 
     * @param loginForm 登录表单，包含手机号和验证码
     * @param session HTTP会话对象
     * @return 登录结果
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号和验证码
        // 获取手机号
        String phone = loginForm.getPhone();
        // 获取用户输入的验证码
        String code = loginForm.getCode();
        // 从session中获取之前保存的验证码
//        String sessionCode = (String) session.getAttribute("code");

        // 1.1 校验手机号格式
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        
//        // 1.2 校验验证码是否正确
//        if (sessionCode == null || !sessionCode.equals(code)) {
//            return Result.fail("验证码错误");
//        }
        // 1.2 校验验证码是否正确从redis中取
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(redisCode == null || !code.equals(redisCode)){
            return Result.fail("验证码错误"); // 验证码错误
        }
        
        // 2. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        
        // 3. 判断用户是否存在
        if (user == null) {
            // 如果用户不存在，则创建新用户
            user = createUserWithPhone(phone);
        }
//
//        // 4. 保存用户信息到session（只保存必要的信息，使用DTO）
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //3使用uuid随机生成token
        String token = UUID.randomUUID().toString();
        //4将user对象转为hash并保存到redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);


        // 将 UserDTO 对象转换为 Map<String, Object> 格式，以便存储到 Redis Hash 中
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        // 忽略 null 值字段，避免将 null 存入 Redis
                        .setIgnoreNullValue(true)
                        // 设置字段值编辑器，统一将所有字段值转换为字符串类型
                        // 原因：StringRedisTemplate 要求 Hash 的 value 必须是 String 类型
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            // 处理 null 值：转为空字符串
                            if (fieldValue == null) {
                                return "";
                            }
                            // 处理数字类型（Long、Integer、Double 等）
                            // 使用 toString() 避免科学计数法等格式问题
                            if (fieldValue instanceof Number) {
                                return ((Number) fieldValue).toString();
                            } 
                            // 处理布尔类型：转为 "true" 或 "false" 字符串
                            else if (fieldValue instanceof Boolean) {
                                return fieldValue.toString();
                            } 
                            // 处理其他类型（String、Date 等）：直接转字符串
                            else {
                                return fieldValue.toString();
                            }
                        }));
        // 4.1 存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 4.2 设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5. 返回登录成功结果
        return Result.ok(token);
    }


    /**
     * 根据手机号创建新用户
     * 
     * @param phone 用户手机号
     * @return 创建的用户对象
     */
    private User createUserWithPhone(String phone) {
        // 创建新用户对象
        User user = new User();
        // 设置手机号
        user.setPhone(phone);
        // 设置昵称：使用系统前缀 + 10位随机字符串
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存用户到数据库
        save(user);
        return user;
    }
}
