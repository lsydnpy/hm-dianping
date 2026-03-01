package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    //通过逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 存储逻辑过期
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }

    //缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPrefix,ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断缓存是否存在
        if(StrUtil.isNotBlank(json)){
            // 3. 存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            return null;
        }
        // 4. 缓存未命中，查询数据库
        R r = dbFallback.apply(id);
        // 5. 判断商铺是否存在
        if(r == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        // 6. 存在，将商铺数据写入 Redis 缓存
        this.set(key, r, time, unit);
        // 7. 返回商铺信息
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期来解决缓存穿透
    public <R, ID> R queryWithPassLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1. 从 Redis 查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断缓存是否存在
        if(StrUtil.isBlank(json)){
            // 3. 不存在，直接返回null
            return null;
        }
        // 3.1、判断逻辑时间是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean(data, type);
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.1.1、未过期
            return r;
        }
        //3.2、已过期，获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;//锁的键
        boolean isLocked = tryLock(lockKey);//
        if(isLocked){
            //3.3、开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    //先查询数据库
                    R r1 = dbFallback.apply(id); // 假设 dbFallback 是一个 Function<ID, R>
                    //再写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }
    // 尝试获取锁
    private boolean tryLock(String key){
        Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);//value为1，表示已锁定
        return BooleanUtil.isTrue(set);

    }
    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
