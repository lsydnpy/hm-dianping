package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    //第一种缓存策略Hash
    @Override
    public Result queryById(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //先判断该key是否存在
        Boolean isKeyExist = stringRedisTemplate.hasKey(key);

        // 1. 从 Redis 查询商铺缓存
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash()
                .entries(key);

        // 2. 判断缓存是否存在
        if (Boolean.TRUE.equals(isKeyExist) && !shopMap.isEmpty()) {
            // 3. 存在，将 Map 转为 Shop 对象并返回
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            return Result.ok(shop);
        }
        //判断命中的是否是空值
        if (Boolean.TRUE.equals(isKeyExist) && shopMap.isEmpty()) {
            return Result.fail("店铺不存在");
        }

        // 4. 缓存未命中，查询数据库
        Shop shop = getById(id);

        // 5. 判断商铺是否存在
        if (shop == null) {
            //将空值写入redis
            stringRedisTemplate.opsForHash().putAll(key, new HashMap<>());
            stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        // 6. 存在，将商铺数据写入 Redis 缓存
        // 将所有字段值转为 String 类型，null 值转为空字符串
        Map<String, Object> shopMapToSave = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) {
                                return "";
                            }
                            return fieldValue.toString();
                        }));
        stringRedisTemplate.opsForHash().putAll(key, shopMapToSave);
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);// 设置缓存过期时间

        // 7. 返回商铺信息
        return Result.ok(shop);
    }

////    //第二种缓存策略String
//    @Override
//    public Result queryById(Long id) {
//        //缓存穿透
////        Shop shop = queryWithPassThrough(id);
////        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //用互斥锁解决缓存击穿
////        Shop shop = queryWithMutex(id);
//        //用逻辑过期解决缓存击穿
////        Shop shop = queryWithPassLogicalExpire(id);
//        Shop shop = cacheClient.queryWithPassLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
//        if (shop == null) {
//            return Result.fail("店铺不存在");
//        }
//        // 返回商铺信息
//        return Result.ok(shop);
//    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithPassLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 1. 从 Redis 查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2. 判断缓存是否存在
//        if(StrUtil.isBlank(shopJson)){
//            // 3. 不存在，直接返回null
//            return null;
//        }
//        // 3.1、判断逻辑时间是否过期
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        LocalDateTime expireTime = redisData.getExpireTime();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 3.1.1、未过期
//            return shop;
//        }
//        //3.2、已过期，获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;//锁的键
//        boolean isLocked = tryLock(lockKey);//
//        if(isLocked){
//            //3.3、开启独立线程
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    //重建缓存
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        return shop;
//    }
//    //互斥锁
//    private Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);//toBean方法将JSON字符串转换为Java对象，返回商铺信息
//        }
//        if (shopJson != null) {
//            return null;
//        }
//        // 4. 尝试获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;//锁的键
//        Shop shop = null;
//        try {
//            boolean isLocked = tryLock(lockKey);
//            // 5. 获取锁失败，返回失败结果
//            if (!isLocked) {
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            Thread.sleep(500);
//            shop = getById(id);
//            if (shop == null) {
//                //将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unlock(lockKey);
//        }
//
//        return shop;
//    }

//    // 尝试获取锁
//    private boolean tryLock(String key){
//        Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);//value为1，表示已锁定
//        return BooleanUtil.isTrue(set);
//
//    }
//    // 释放锁
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//    //缓存穿透
//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 1. 从 Redis 查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2. 判断缓存是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            // 3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 判断命中的是否是空值
//        if (shopJson != null) {
//            return null;
//        }
//        // 4. 缓存未命中，查询数据库
//        Shop shop = getById(id);
//        // 5. 判断商铺是否存在
//        if(shop == null){
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
//            return null;
//        }
//        // 6. 存在，将商铺数据写入 Redis 缓存
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.SECONDS);
//        // 7. 返回商铺信息
//        return shop;
//    }



//    public void saveShop2Redis(Long id, Long expireTime) {
//        // 1、查询店铺数据
//        Shop shop = getById(id);
//        // 2、封装成逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
//
//        // 3、保存到 Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)//开启事务，回滚异常，确保数据一致性
    public Result update(Shop shop) {
        // 1. 首先校验shop对象是否为null（核心修复点）
        if (shop == null) {
            log.warn("更新店铺失败：shop对象为null");
            return Result.fail("店铺信息不能为空");
        }
        if (shop.getId() == null) {
            return Result.fail("店铺ID不能为空");
        }
        // 1. 更新数据库
        updateById(shop);

        // 2. 注册事务同步器，确保事务提交后再删除缓存
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 事务提交成功后执行缓存删除
                stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
            }

            @Override
            public void afterCompletion(int status) {
                // 事务完成后（无论成功失败）的兜底处理
                if (status != STATUS_COMMITTED) {
                    // 可选：事务回滚时的日志记录或补偿逻辑
                    log.error("店铺更新事务回滚，shopId:{}");
                }
            }
        });

        return Result.ok();
    }
}
