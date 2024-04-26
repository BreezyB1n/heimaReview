package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
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
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存击穿
        Shop shop = queryWithMutex(id);
        // 判断是否为null
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 查询并使用互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 从redis中取出店铺
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 判断店铺是否在redis中存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 存在，将Json串转为对象
            Shop shop = BeanUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        // 实现缓存重建
        // 获取互斥锁
        String lockKey = null;
        Shop shop = null;
        try {
            lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);
            // 判断获取锁是否成功
            if (!isLock) {
                do {
                    // 不成功，进入休眠并重新查询
                    Thread.sleep(50);
                    queryWithMutex(id);
                } while (!isLock);
            }
            // 再次查询redis中缓存是否存在
            // 如果存在直接返回即可
            String twiceShopJson = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isNotBlank(twiceShopJson)) {
                unlock(lockKey);
                shop = BeanUtil.toBean(twiceShopJson, Shop.class);
                return shop;
            }
            // 二次查询缓存不存在
            // 成功，根据数据库查询
            // 不存在，从数据库中查找
            shop = getById(id);
            // 模拟重建延时
            Thread.sleep(200);
            // 判断数据库中是否存在该店铺
            if (shop == null) {
                // 不存在写入一个空值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 存在，写入redis缓存中并返回
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }
        // 返回商户
        return shop;
    }

    public Shop queryWithLogicExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 从redis中取出店铺
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 判断店铺是否在redis中存在
        if (StrUtil.isBlank(shopJson)) {
            // 不存在直接返回null
            return null;
        }

        // 命中 反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);

        // 检查是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 没有过期，直接返回shop
            return shop;
        }

        // 如果过期，获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 检验获取锁是否成功
        if (isLock) {
            // 再次检验缓存是否过期，如果过期则无需重建缓存
            shopJson = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isBlank(shopJson)) {
                // 不存在直接返回null
                return null;
            }
            // 命中 反序列化为对象
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            data = (JSONObject) redisData.getData();
            shop = JSONUtil.toBean(data, Shop.class);

            // 检查是否过期
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 没有过期，直接返回shop
                return shop;
            }
            // 如果还是过期
            // TODO: 成功，则开启一个新线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 返回过期的商户
        return shop;
    }

    /**
     * 设置逻辑过期时间逻辑
     * @param id 用户id
     * @param expireSeconds 逻辑过期时间
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 查询店铺数据
        Shop shop = getById(id);
        // 模拟缓存延迟
        Thread.sleep(200);
        // 封装逻辑过期事件
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询并解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 从redis中取出店铺
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 判断店铺是否在redis中存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 存在，将Json串转为对象
            Shop shop = BeanUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        // 不存在，从数据库中查找
        Shop shop = getById(id);
        // 判断数据库中是否存在该店铺
        if (shop == null) {
            // 不存在写入一个空值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 存在，写入redis缓存中并返回
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回商户
        return shop;
    }

    /**
     * 创建互斥锁
     * @param key 键值
     * @return 加锁是否成功
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 防止出现拆箱时发生null指针异常，这里使用Hutool工具包
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
