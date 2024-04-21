package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByCache() {
        // 字符串存储
        // 从redis中获取商店类型
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_TYPE_KEY);
        // 判断redis是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 缓存命中
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 没有存在，从数据库中查找
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 判断是否存在
        if (shopTypeList == null) {
            return Result.fail("该分类不存在！");
        }
        // 存在，放入redis中
        stringRedisTemplate.opsForValue().set(CACHE_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList));
        // 返回信息
        return Result.ok(shopTypeList);
//        return Result.ok();
    }
}
