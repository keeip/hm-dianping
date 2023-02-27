package com.hmdp.controller;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList=new ArrayList<>();
        String shopTypeList = stringRedisTemplate.opsForValue().get(RedisConstants.LIST_SHOPTYPE);
        typeList= JSONUtil.toList(shopTypeList,ShopType.class);
        if (shopTypeList!=null){
            return Result.ok(typeList);
        }
        typeList=typeService
                .query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(RedisConstants.LIST_SHOPTYPE,JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}
