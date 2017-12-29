package com.wuhao.sc.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {
    private static RedisTemplate<String, Object> cacheTemplate;

    public static long TIME_OUT = 7 * 24 * 60 * 60;

    public static <T> T get(String key, Class<T> theClass) {
        return (T) getCacheTemplate().opsForValue().get(key);
    }

    public static boolean hashKey(String key){
        return getCacheTemplate().hasKey(key);
    }

    public static void delKeys(List<String> keys){
        getCacheTemplate().delete(keys);
    }

    public static void delKey(String key){
        getCacheTemplate().delete(key);
    }

    public static boolean getBit(String key, long offset) {
        return getCacheTemplate().opsForValue().getBit(key, offset);
    }

    public static Object get(String key) {
        return getCacheTemplate().opsForValue().get(key);
    }

    public static void set(String key, Object data) {
        getCacheTemplate().opsForValue().set(key, data, TIME_OUT, TimeUnit.SECONDS);
    }

    public static void set(String key, Object data, long timeout) {
        getCacheTemplate().opsForValue().set(key, data, timeout, TimeUnit.SECONDS);
    }

    public static Object hget(String key, String hashKey) {
        return getCacheTemplate().opsForHash().get(key, hashKey);
    }

    public static void hset(String key, String hashKey, Object data) {
        getCacheTemplate().opsForHash().put(key, hashKey, data);
        getCacheTemplate().expire(key, TIME_OUT, TimeUnit.SECONDS);
    }

    public static void hset(String key, String hashKey, Object data, long timeout) {
        getCacheTemplate().opsForHash().put(key, hashKey, data);
        getCacheTemplate().expire(key, timeout, TimeUnit.SECONDS);
    }

    public static void hmset(String key, Map<String, Object> data) {
        getCacheTemplate().opsForHash().putAll(key, data);
        getCacheTemplate().expire(key, TIME_OUT, TimeUnit.SECONDS);
    }

    public static void hmset(String key, Map<String, Object> data, long timeout) {
        getCacheTemplate().opsForHash().putAll(key, data);
        getCacheTemplate().expire(key, timeout, TimeUnit.SECONDS);
    }

    public static List<Object> hmget(String key, List<Object> hashKeys) {
        return getCacheTemplate().opsForHash().multiGet(key, hashKeys);
    }

    public static List multiGet(List ids) {
        return getCacheTemplate().opsForValue().multiGet(ids);
    }

    public static void multiSet(Map<String, Object> dataMap) {
        getCacheTemplate().opsForValue().multiSet(dataMap);
    }

    public static void delete(String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (key != null) {
                result.add(key);
            }
        }
        getCacheTemplate().delete(result);
    }

    public static Long increment(String key) {
        return getCacheTemplate().opsForValue().increment(key, 1);
    }

    public static Long increment(String key, long delta) {
        return getCacheTemplate().opsForValue().increment(key, delta);
    }

    public static Long decrement(String key) {
        return increment(key, -1);
    }

    public static Long decrement(String key, long delta) {
        return increment(key, -delta);
    }

    public static boolean setIfAbsent(final String key, final Object value, final long millis) {
        boolean result = getCacheTemplate().opsForValue().setIfAbsent(key, value);
        getCacheTemplate().expire(key, millis, TimeUnit.MILLISECONDS);
        return result;
    }

    public static RedisTemplate<String, Object> getCacheTemplate() {
        return cacheTemplate;
    }

    @Autowired
    private void setCacheTemplate(RedisTemplate cacheTemplate) {
        this.cacheTemplate = cacheTemplate;
    }

}
