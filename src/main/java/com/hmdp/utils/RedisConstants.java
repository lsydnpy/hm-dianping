package com.hmdp.utils;

/**
 * Redis常量类
 * 用于统一管理Redis中的key前缀和TTL过期时间
 */
public class RedisConstants {
    // 登录验证码相关
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L; // 验证码有效期2分钟
    
    // 登录用户token相关
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 3L; // 有效期3分钟
    
    // 缓存空值TTL（解决缓存穿透）
    public static final Long CACHE_NULL_TTL = 2L; // 空值缓存2分钟
    
    // 店铺缓存相关
    public static final Long CACHE_SHOP_TTL = 30L; // 店铺缓存30分钟
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    // 店铺类型缓存相关
    public static final Long CACHE_SHOP_TYPE_TTL = 3L; // 店铺类型缓存30分钟
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:list";
    
    // 店铺分布式锁相关
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L; // 锁有效期10秒
    
    // 秒杀库存相关
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    
    // 博客点赞相关
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    
    // 用户feed流相关
    public static final String FEED_KEY = "feed:";
    
    // 店铺地理位置相关
    public static final String SHOP_GEO_KEY = "shop:geo:";
    
    // 用户签到相关
    public static final String USER_SIGN_KEY = "sign:";
}
