package com.kyexpress.ec.cache;

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

    static String[] byIdMethod = new String[] { "get", "getByIds", "disable", "enable" };

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
//        System.out.println("-----------------------------");
        System.out.println(clazz.getName() + "  " + method.getName());
        if(cache == null || cache.skip()) {
            return joinPoint.proceed();
        }
        for (String mstring : updateMethodPres) {
            if(method.getName().startsWith(mstring)) {
                return cacheUpdate(joinPoint);
            }
        }
        return cacheQuery(joinPoint);
    }

    private Map<Class<?>, Set<String>> getTableRowKeys(ECCache cache, ProceedingJoinPoint joinPoint, Method method) throws Exception {
        boolean byIdMethodFlag = false;
        String methodName = method.getName();
        for (String mname : byIdMethod) {
            if(mname.equals(methodName)) {
                byIdMethodFlag = true;
                break;
            }
        }

        method.getParameterAnnotations();


        Map<Class<?>, Set<String>> tableCacheKey = getTableCacheKey(cache);

        Map<Class<?>, Set<String>> tableRowKeys = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        for (Class<?> cacheClass : cache.value()) {
            Set<String> rowKeys = new HashSet<>();
            for (Object arg : args) {
                rowKeys.addAll(fetchFieldValueList(arg, cacheClass, tableCacheKey, byIdMethodFlag));
            }
            if(rowKeys.size() > 0) {
                tableRowKeys.put(cacheClass, rowKeys);
            }
        }
        return tableRowKeys;
    }

    private Map<Class<?>, Set<String>> getTableCacheKey(ECCache cache) {
        Map<Class<?>, Set<String>> tableCacheKey = new HashMap<>();
        for (Class<?> cacheClass : cache.value()) {
            Set<String> cacheKeyField = new HashSet<>();
            CacheKey cacheKey = cacheClass.getAnnotation(CacheKey.class);
            if(cacheKey == null || cacheKey.value().length == 0) {
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
        return tableCacheKey;
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

    private Set<String> fetchFieldValueList(Object arg, Class<?> tmpCacheClass, Map<Class<?>, Set<String>> tableCacheKey, boolean byIdMethodFlag) throws Exception {
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
                rowKeys.add(field + "_" + fieldValue);
            }
            return rowKeys;
        } else if (Number.class.isAssignableFrom(arg.getClass())) {
            if(byIdMethodFlag && cacheField.contains("id")) {
                rowKeys.add("id" + "_" + arg);
            }
        } else if (arg.getClass().isArray()) {
            Object[] tmpArgs = (Object[]) arg;
            for (Object tmpArg : tmpArgs) {
                rowKeys.addAll(fetchFieldValueList(tmpArg, tmpCacheClass, tableCacheKey, byIdMethodFlag));
            }
        } else if (Collection.class.isAssignableFrom(arg.getClass())) {
            Collection<?> tmpArgs = (Collection<?>) arg;
            for (Object tmpArg : tmpArgs) {
                rowKeys.addAll(fetchFieldValueList(tmpArg, tmpCacheClass, tableCacheKey, byIdMethodFlag));
            }
        } else {
            System.out.println("none cache key...");
        }
        return rowKeys;
    }

    public Object cacheUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        ECCache cache = null;
        Map<Class<?>, Set<String>> tableRowKeys = null;
        try {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            cache = method.getAnnotation(ECCache.class);
            Class<?> clazz = joinPoint.getTarget().getClass();
            if(cache == null) {
                cache = clazz.getAnnotation(ECCache.class);
            }
            tableRowKeys = getTableRowKeys(cache, joinPoint, method);
//            System.out.println(tableRowKeys);
            System.out.println("--------------------cacheUpdate--------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Object data = joinPoint.proceed();
        try {
            Long version = System.currentTimeMillis();
            Class<?>[] keys = cache.value();
            for (Class<?> key : keys) {
                Map<String, Object> cacheVersions = new HashMap<>();
                Set<String> rowKeys = tableRowKeys.get(key);
                if(rowKeys == null || rowKeys.size() == 0) {
                    CacheUtils.delete(key.getName());
                }else{
                    for (String rowkey : rowKeys) {
                        cacheVersions.put(rowkey, version);
                    }
                }
                cacheVersions.put("tableVersion", version);
                CacheUtils.hmset(key.getName(), cacheVersions);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    public Object cacheQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        ECCache cache = null;
        String hashKey = null;
        Map<Class<?>, Set<String>> tableRowKeys = null;
        Map<Class<?>, Map<String, Object>> dataVersions = new LinkedHashMap<>();
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

            tableRowKeys = getTableRowKeys(cache, joinPoint, method);

            System.out.println("--------------------cacheQuery--------------------");
//            System.out.println(tableRowKeys);

            Object[] args = joinPoint.getArgs();
            int argsLength = args.length;
            String[] argsArray = new String[argsLength];
            for (int i = 0; i < argsLength; i++) {
                argsArray[i] = ObjectAnalyzerUtils.objectToString(args[i]);
            }
            hashKey = clazz.getSimpleName() + "_" + method.getName() + "_" + Arrays.toString(argsArray);
            hashKey = clazz.getSimpleName() + "_"  + EncryptUtils.md5(hashKey);

            Class<?>[] keys = cache.value();
            for (Class<?> key : keys) {
                List<Object> cacheVersionKeys = new ArrayList<>();
                Set<String> rowKeys = tableRowKeys.get(key);
                if(rowKeys != null && rowKeys.size() > 0) {
                    for (String rowkey : rowKeys) {
                        cacheVersionKeys.add(rowkey);
                    }
                }else{
                    cacheVersionKeys.add("tableVersion");
                }
                List<Object> list = CacheUtils.hmget(key.getName(), cacheVersionKeys);
                Map<String, Object> cacheVersions = new LinkedHashMap<>();
                int size = cacheVersionKeys.size();
                for (int i = 0; i < size; i++) {
                    cacheVersions.put(cacheVersionKeys.get(i).toString(), list.get(i));
                }
                if(cacheVersions.containsValue(null)) {
                    Map<String, Object> cacheVersionMap = new HashMap<>();
                    Object tableVersion = null;
                    if(cacheVersions.get("tableVersion") != null) {
                        tableVersion = cacheVersions.get("tableVersion");
                    }else{
                        tableVersion = System.currentTimeMillis();
                        cacheVersionMap.put("tableVersion", tableVersion);
                    }
                    for (String fieldKey : cacheVersions.keySet()) {
                        if(cacheVersions.get(fieldKey) == null) {
                            cacheVersions.put(fieldKey, tableVersion);
                        }
                        cacheVersionMap.put(fieldKey, tableVersion);
                    }
                    CacheUtils.hmset(key.getName(), cacheVersionMap);
                    System.out.println("========================");
                }
                dataVersions.put(key, cacheVersions);
            }
//            System.out.println(dataVersions);
            Object value = CacheUtils.get(hashKey);
            if(value != null && value instanceof Map) {
                Map<String, Object> cacheData = (Map<String, Object>) value;
                if(cacheData.containsKey("version") && cacheData.containsKey("data")) {
                    Map<String, Object> version = (Map<String, Object>) cacheData.get("version");
                    if(dataVersions.toString().equals(version.toString())) {
                        System.out.println("cached ....");
                        return cacheData.get("data");
                    }
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        Object data = joinPoint.proceed();
        try {
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("data", data);
            cacheData.put("version", dataVersions);
            CacheUtils.set(hashKey, cacheData, cache.expire());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

}
