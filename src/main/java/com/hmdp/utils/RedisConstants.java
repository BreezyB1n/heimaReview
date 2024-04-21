package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_USER_KEY = "login:token:";

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    // session验证码的常量值
    public static final String SESSION_CODE = "code";
    // 用户信息常量值
    public static final String USER_CONSTANT = "user";
    //Redis中短信验证码有效期
    public static final Long LOGIN_CODE_TTL = 2L;
    // Redis验证码Key前缀
    public static final String LOGIN_CODE_KEY = "login:code:";
    // 用户Token前缀
    public static final String LOGIN_TOKEN_KEY = "login:token:";
    // 用户信息有效期
    public static final Long LOGIN_USER_TTL = 30L;
    // 店铺类型token
    public static final String CACHE_TYPE_KEY = "cache:type:";
}
