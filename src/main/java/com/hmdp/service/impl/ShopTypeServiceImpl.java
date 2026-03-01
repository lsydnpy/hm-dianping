package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = CACHE_SHOP_TYPE_KEY;
        List<ShopType> typeList = null;

        try {
            // 1. 从 Redis 查询店铺类型列表缓存（opsForValue.get 操作String类型）
            String typeListJson = stringRedisTemplate.opsForValue().get(key);

            // 2. 判断缓存是否存在（非空且非空白字符串）
            if (StrUtil.isNotBlank(typeListJson)) {
                // 3. 缓存命中：JSON转对象列表返回
                typeList = JSONUtil.toList(typeListJson, ShopType.class);
                log.info("Redis缓存命中，直接返回店铺类型列表");
                return Result.ok(typeList);
            }

            // 4. 缓存未命中：查询数据库（按sort升序）
            typeList = query().orderByAsc("id").list();
            log.info("Redis缓存未命中，查询数据库获取店铺类型列表");

            // 5. 数据库无数据：缓存空值（解决缓存穿透）
            if (typeList == null || typeList.isEmpty()) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺类型不存在");
            }

            // 6. 数据库有数据：写入Redis缓存（opsForValue.set 操作String类型）
            String jsonStr = JSONUtil.toJsonStr(typeList);
            stringRedisTemplate.opsForValue().set(
                    key,
                    jsonStr,
                    CACHE_SHOP_TYPE_TTL,
                    TimeUnit.MINUTES
            );
            log.info("店铺类型列表已写入Redis缓存，Key: {}, TTL: {}分钟", key, CACHE_SHOP_TYPE_TTL);

        } catch (Exception e) {
            log.error("查询店铺类型列表异常", e);
            // 缓存异常时，直接返回数据库结果（兜底）
            if (typeList == null) {
                typeList = query().orderByAsc("sort").list();
            }
            if (typeList == null || typeList.isEmpty()) {
                return Result.fail("查询店铺类型失败");
            }
            return Result.ok(typeList);
        }

        // 7. 返回结果
        return Result.ok(typeList);
    }
}