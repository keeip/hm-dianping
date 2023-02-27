package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.JNDIUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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

    @Override
    public Result queryById(Long id) {
        return queryPass(id);
    }
    //获取锁
    private boolean tryLock(String key){
       Boolean flag= stringRedisTemplate.opsForValue().setIfAbsent(key,"1",50,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //解锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
    //去做缓存击穿问题
    private Result queryPass(Long id)  {
        //首先查询缓存
        String cacheShop = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //缓存有直接转化为对象返回
        if (StrUtil.isNotBlank(cacheShop)){
            Shop shop= JSONUtil.toBean(cacheShop,Shop.class);
            return Result.ok(shop);
        }
        //cacheshop没有值，则cache要么为空要么为null
        if ("".equals(cacheShop)){
            return Result.fail("商铺不存在");
        }
        //查询为null值，为了防止大量请求去请求数据库和重构缓存，给此次行为加锁，拿到锁的线程才能进行请求数据库和缓存重构
        String lockKey="lock:shop"+id;
        //假如未拿到锁,线程进入休眠,休眠结束重新查询redis，直到其他线程完成重构
        try {
            if (!tryLock(lockKey)){
                Thread.sleep(50);
                queryPass(id);
            }
            //成功拿到锁，去查询数据库重构缓存
            //查找数据库，
            Shop shop=getById(id);
            //数据库没有返回错误
            if (shop==null){
                //如果没有则缓存空值，防止再次访问数据库
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",5,TimeUnit.MINUTES);
                return Result.fail("商铺不存在");
            }
            //数据库中有返回转为json存入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //返回
            return Result.ok(shop);
        }
        catch (Exception exception){
            throw new RuntimeException(exception);
        }
        finally {
            //解锁
            unlock(lockKey);
        }
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId()==null){
            return Result.fail("id不能为空");
        }
        //先更新数据库
        updateById(shop);
        //在删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

}
