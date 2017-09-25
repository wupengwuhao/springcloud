package com.kyexpress.ec.item.provider.utils;

import com.kyexpress.ec.item.api.CacheKey;
import com.kyexpress.framework.cache.CacheUtils;
import com.kyexpress.framework.model.GenericModel;
import com.kyexpress.framework.utils.EncryptUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@Aspect
@Component
public class ECCacheAspect {

    static String[] updateMethodPres = new String[] { "insert", "add", "update", "edit", "delete", "remove", "disable", "enable" };

//    @Pointcut("within(com.kyexpress.ec..*) && @within(org.springframework.web.bind.annotation.RestController) && execution(public * *(..))")
    public void controller() {
    }

    @Pointcut("within(com.kyexpress..*) && this(com.kyexpress.framework.service.GenericService) && execution(public * *(..))")
    public void service() {
    }
    @Around("service()")
    public Object cache(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        ECCache cache = method.getAnnotation(ECCache.class);
        Class<?> clazz = joinPoint.getTarget().getClass();
        if(cache == null) {
            cache = clazz.getAnnotation(ECCache.class);
        }
        System.out.println("-----------------------------");
        System.out.println(clazz.getName() + "  " + method.getName());
        if(cache == null || cache.skip()) {
            return joinPoint.proceed();
        }
        Map<Class<?>, Set<String>> tableCacheKey = new HashMap<>();
        for (Class<?> cacheClass : cache.value()) {
            Set<String> cacheKeyField = new HashSet<>();
            CacheKey cacheKey = cacheClass.getAnnotation(CacheKey.class);
            if(cacheKey == null || cacheKey.value().length == 0) {
                System.out.println(cacheClass.getName());
                if(cacheClass.getSuperclass() == GenericModel.class) {
                    System.out.println("cacheClass.getSuperclass() == GenericModel.class");
                }
                if(GenericModel.class.isAssignableFrom(cacheClass)) {
                    cacheKeyField.add("id");
                }
            }else{
                for (String field : cacheKey.value()) {
                    cacheKeyField.add(field);
                }
            }
            if(cacheKeyField.size() > 0) {
                tableCacheKey.put(cacheClass, cacheKeyField);
            }
        }

        System.out.println(tableCacheKey);

        Map<Class, Set<String>> tableRowKeys = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            System.out.println(arg.getClass().getName() );
            Set<String> rowKeys = new HashSet<>();
            Class<?> cacheClass = null;
            for (Class<?> tmpCacheClass : cache.value()) {
                cacheClass = tmpCacheClass;
                rowKeys.addAll(iiiiiiiiiiiiiiiiiiiiii(arg, tmpCacheClass, tableCacheKey));
            }
            if(rowKeys.size() > 0) {
                tableRowKeys.put(cacheClass, rowKeys);
            }
        }

        System.out.println(tableRowKeys);

        for (String mstring : updateMethodPres) {
            if(method.getName().startsWith(mstring)) {
                return cacheUpdate(joinPoint);
            }
        }
        return cacheQuery(joinPoint);
    }

    private Field getField(String fieldName, Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if(field.getName().equals(fieldName)) {
                return field;
            }
        }
        return getField(fieldName, clazz.getSuperclass());
    }

    private Set<String> iiiiiiiiiiiiiiiiiiiiii(Object arg, Class<?> tmpCacheClass, Map<Class<?>, Set<String>> tableCacheKey) throws Exception {
        System.out.println("iiiiiiiiiiiiiiiiiiiiii: "+tmpCacheClass.getName());
        Set<String> rowKeys = new HashSet<>();
        Set<String> cacheField = tableCacheKey.get(tmpCacheClass);
        if(cacheField == null) {
            return rowKeys;
        }
        if (arg.getClass().isAssignableFrom(tmpCacheClass)) {
            for (String field : cacheField) {
                Field fieldObject = getField(field, tmpCacheClass);
                fieldObject.setAccessible(true);
                Object fieldValue = fieldObject.get(arg);
                if(fieldValue == null) {
                    continue;
                }
                rowKeys.add(field + fieldValue);
            }
            return rowKeys;
        } else if (Number.class.isAssignableFrom(arg.getClass())) {
            System.out.println("Long.class");
            rowKeys.add("id" + arg);
        } else if (arg.getClass().isArray()) {
            System.out.println("isArray");
            Object[] tmpArgs = (Object[]) arg;
            for (Object tmpArg : tmpArgs) {
                rowKeys.addAll(iiiiiiiiiiiiiiiiiiiiii(tmpArg, tmpCacheClass, tableCacheKey));
            }
        } else if (Collection.class.isAssignableFrom(arg.getClass())) {
            System.out.println("Collection");
            Collection<?> tmpArgs = (Collection<?>) arg;
            for (Object tmpArg : tmpArgs) {
                rowKeys.addAll(iiiiiiiiiiiiiiiiiiiiii(tmpArg, tmpCacheClass, tableCacheKey));
            }
        } else {
            System.out.println("other");
        }
        return rowKeys;
    }

    public Object cacheUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        ECCache cache = null;
        try {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            cache = method.getAnnotation(ECCache.class);
            Class<?> clazz = joinPoint.getTarget().getClass();
            if(cache == null) {
                cache = clazz.getAnnotation(ECCache.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Object data = joinPoint.proceed();
        try {
            Class[] keys = cache.value();
            for (Class key : keys) {
                CacheUtils.delete(key.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    public Object cacheQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        ECCache cache = null;
        String hashKey = null;
        Class[] keys = new Class[0];
        try {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            cache = method.getAnnotation(ECCache.class);
            Class<?> clazz = joinPoint.getTarget().getClass();
            if(cache == null) {
                cache = clazz.getAnnotation(ECCache.class);
            }
            if(cache == null || cache.skip()) {
                return joinPoint.proceed();
            }
            Object[] args = joinPoint.getArgs();
            int argsLength = args.length;
            String[] argsArray = new String[argsLength];
            for (int i = 0; i < argsLength; i++) {
                argsArray[i] = ObjectAnalyzerUtils.objectToString(args[i]);
            }
            hashKey = clazz.getSimpleName() + "_" + method.getName() + "_" + Arrays.toString(argsArray);
            hashKey = clazz.getSimpleName() + "_"  + EncryptUtils.md5(hashKey);
            keys = cache.value();
            Object value = null;
            for (Class key : keys) {
                value = CacheUtils.hget(key.getName(), hashKey);
                if(value == null) {
                    break;
                }
            }
            if(value != null) {
                value = CacheUtils.get(hashKey);
            }
            if(value != null) {
                return value;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        Object data = joinPoint.proceed();
        try {
            CacheUtils.set(hashKey, data, cache.expire());
            for (Class key : keys) {
                CacheUtils.hset(key.getName(), hashKey, 1, cache.expire());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

}
