package com.kyexpress.ec.cache;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;

import redis.clients.jedis.BinaryJedisCluster;

import com.onetalk.cache.CacheKey;
import com.onetalk.cache.CacheKey.KEYTYPE;
import com.onetalk.cache.ECCache;
import com.onetalk.cache.ECCache.CACHETYPE;
import com.onetalk.watch.util.ListUtil;
import com.onetalk.watch.util.MD5Util;
import com.onetalk.watch.util.SerializeUtil;

@Aspect
public class ECCacheAop implements Ordered {

	Logger logger = Logger.getLogger(this.getClass().getName());

	@Autowired
	private BinaryJedisCluster jedisCluster;


	public Object cacheUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
		Object result = null;
		execute:{
			byte[] tableKey = null;
			Object[] arguments = joinPoint.getArgs();
			MethodSignature joinPointObject = (MethodSignature) joinPoint.getSignature();
			Method method = joinPointObject.getMethod();
			ECCache ecCache = method.getAnnotation(ECCache.class);
			if(ecCache == null) {
				ecCache = joinPoint.getTarget().getClass().getAnnotation(ECCache.class);
			}
			Map<Class<?>, Set<String>> updateRowKeys = new HashMap<>();
			Class<?>[] objectClassArr = ecCache.value();

			Set<String> rowKeyList = new HashSet<String>();
			for (Object argument : arguments) {
				for (Class<?> objectClass : objectClassArr) {
					CacheKey cacheKey = objectClass.getAnnotation(CacheKey.class);
					String[] cacheKeyFields = cacheKey.keys();
					if(cacheKeyFields == null) {
						cacheKeyFields = new String[]{"id"};
					}
					if(argument.getClass().isAssignableFrom(objectClass)) {
						for (String field : cacheKeyFields) {
							Field fieldObject = objectClass.getDeclaredField(field);
							fieldObject.setAccessible(true);
							Object fieldValue = fieldObject.get(argument);
							if(fieldValue == null) {
								continue;
							}
							rowKeyList.add(field + fieldValue);
						}
					}else if(argument.getClass().isAssignableFrom(Number[].class)){
						Number[] tmpArgument = (Number[]) argument;
						if(tmpArgument.length == 0) {
							break ;
						}
						if(tmpArgument[0].getClass().isAssignableFrom(objectClass)) {
							for (String field : cacheKeyFields) {
								Field fieldObject = objectClass.getDeclaredField(field);
								fieldObject.setAccessible(true);
								Object fieldValue = fieldObject.get(argument);
								if(fieldValue == null) {
									continue;
								}
								rowKeyList.add(field + fieldValue);
							}
						}
					}else if(argument.getClass().isAssignableFrom(Collection.class)) {
						Collection<?> tmpArgument = (Collection<?>) argument;
						if (tmpArgument.iterator().hasNext()) {
							break ;
						}
						if(tmpArgument.iterator().next().getClass().isAssignableFrom(objectClass)) {
							for (String field : cacheKeyFields) {
								Field fieldObject = objectClass.getDeclaredField(field);
								fieldObject.setAccessible(true);
								Object fieldValue = fieldObject.get(argument);
								if(fieldValue == null) {
									continue;
								}
								rowKeyList.add(field + fieldValue);
							}
						}
					}
					if(rowKeyList.size() > 0) {
						updateRowKeys.put(objectClass, rowKeyList);
						break ;
					}
				}


			}

			for (Class<?> objectClass : objectClassArr) {
				CacheKey cacheKey = objectClass.getAnnotation(CacheKey.class);





				if(rowKeyList.size() > 0) {
					updateRowKeys.put(objectClass, rowKeyList);
				}
			}


			CacheKey cacheKey = objectClass.getAnnotation(CacheKey.class);
			if(cacheKey != null && cacheKey.keys().length == 0) {
				cacheKey = null;
			}
			List<Object> argsObjectList = new ArrayList<Object>();
			if(cacheKey != null && arguments.length == 1) {
				if(arguments[0] instanceof List) {
					argsObjectList.addAll((List)arguments[0]);
				}else if(objectClass.isAssignableFrom(arguments[0].getClass())){
					argsObjectList.add(arguments[0]);
				}
			}
			Set<String> rowKeyList = new HashSet<String>();
			for (Object argsObject : argsObjectList) {
				if(! objectClass.isAssignableFrom(argsObject.getClass())) {
					break;
				}
				List<String> rowKeyList2 = new ArrayList<String>();
				for (String field : cacheKey.keys()) {
					Field fieldObject = objectClass.getDeclaredField(field);
					fieldObject.setAccessible(true);
					Object fieldValue = fieldObject.get(argsObject);
					if(fieldValue == null) {
						continue;
					}
					rowKeyList2.add(field + fieldValue);
				}
				if(cacheKey.type() == KEYTYPE.ADD && cacheKey.keys().length == rowKeyList2.size()) {
					rowKeyList.add(ListUtil.toString(rowKeyList2));
					if(ECCache.type() == CACHETYPE.UPDATE) {
						rowKeyList.addAll(rowKeyList2);
					}
				}else{
					rowKeyList.addAll(rowKeyList2);
				}
				if(ECCache.type() == CACHETYPE.QUERY) {
					break;
				}
			}
			tableKey = objectClass.getName().getBytes();
			byte[] dataKey = null;
			if(ECCache.type() == CACHETYPE.QUERY) {
				dataKey = getCacheKey(ECCache, method, arguments);
				byte[] bytes = jedisCluster.get(dataKey);
				Map<String, Object> data = null;
				if(bytes != null) {
					data = (Map<String, Object>) SerializeUtil.deserialize(bytes);
				}
				String dataRowKey = null;
				Long tableVersion = null;
				if(data != null) {
					if(rowKeyList.size() > 0) {
						dataRowKey = getRowKeyVersion(tableKey, rowKeyList);
						if(dataRowKey.toString().equals(data.get("rowV").toString())) {
							return data.get("data");
						}
					}else{
						tableVersion = getTableVersion(tableKey);
						if(tableVersion.equals(Long.valueOf(data.get("tableV").toString()))) {
							return data.get("data");
						}
					}
					data = null;
				}
				if(data == null) {
					result = joinPoint.proceed();
					if(tableVersion == null) {
						tableVersion = getTableVersion(tableKey);
					}
					if(dataRowKey == null) {
						dataRowKey = getRowKeyVersion(tableKey, rowKeyList);
					}
					data = new HashMap<String, Object>();
					data.put("data", result);
					data.put("tableV", tableVersion);
					data.put("rowV", dataRowKey == null ? "" : dataRowKey.toString());
					if (ECCache.expire() > 0) {
						jedisCluster.setex(dataKey, ECCache.expire(), SerializeUtil.serialize(data));
					}else{
						jedisCluster.set(dataKey, SerializeUtil.serialize(data));
					}
				}
				break execute;
			}
			if (ECCache.type() == CACHETYPE.UPDATE
					|| ECCache.type() == CACHETYPE.DELETE
					|| ECCache.type() == CACHETYPE.ADD) {
				result = joinPoint.proceed();
				Class<?> returnType = method.getReturnType();
				if(returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
					if((Boolean)result == false) {
						break execute;
					}
				}
				if(tableKey != null) {//重置版本号
					updateTableVersion(tableKey);
				}
				for (String rowKey : rowKeyList) {
					updateTableRowVersion(tableKey, rowKey.getBytes());
				}
				break execute;
			}
		}
		return result;
	}

	@Around("@annotation(ECCache)")
	public Object methodCacheHold(ProceedingJoinPoint joinPoint) throws Throwable {
		Object result = null;
		execute:{
			byte[] tableKey = null;
			Object[] arguments = joinPoint.getArgs();
			MethodSignature joinPointObject = (MethodSignature) joinPoint.getSignature();
			Method method = joinPointObject.getMethod();
			ECCache ecCache = method.getAnnotation(ECCache.class);
			if(ecCache == null) {
				ecCache = joinPoint.getTarget().getClass().getAnnotation(ECCache.class);
			}
			Class<?>[] objectClass = ecCache.value();



			CacheKey cacheKey = objectClass.getAnnotation(CacheKey.class);
			if(cacheKey != null && cacheKey.keys().length == 0) {
				cacheKey = null;
			}
			List<Object> argsObjectList = new ArrayList<Object>();
			if(cacheKey != null && arguments.length == 1) {
				if(arguments[0] instanceof List) {
	            	argsObjectList.addAll((List)arguments[0]);
	            }else if(objectClass.isAssignableFrom(arguments[0].getClass())){
	            	argsObjectList.add(arguments[0]);
	            }
			}
			Set<String> rowKeyList = new HashSet<String>();
        	for (Object argsObject : argsObjectList) {
        		if(! objectClass.isAssignableFrom(argsObject.getClass())) {
        			break;
        		}
        		List<String> rowKeyList2 = new ArrayList<String>();
        		for (String field : cacheKey.keys()) {
        			Field fieldObject = objectClass.getDeclaredField(field);
        			fieldObject.setAccessible(true);
        			Object fieldValue = fieldObject.get(argsObject);
        			if(fieldValue == null) {
        				continue;
        			}
        			rowKeyList2.add(field + fieldValue);
				}
    			if(cacheKey.type() == KEYTYPE.ADD && cacheKey.keys().length == rowKeyList2.size()) {
    				rowKeyList.add(ListUtil.toString(rowKeyList2));
        			if(ECCache.type() == CACHETYPE.UPDATE) {
        				rowKeyList.addAll(rowKeyList2);
        			}
    			}else{
    				rowKeyList.addAll(rowKeyList2);
    			}
    			if(ECCache.type() == CACHETYPE.QUERY) {
    				break;
    			}
			}
			tableKey = objectClass.getName().getBytes();
			byte[] dataKey = null;
			if(ECCache.type() == CACHETYPE.QUERY) {
				dataKey = getCacheKey(ECCache, method, arguments);
				byte[] bytes = jedisCluster.get(dataKey);
				Map<String, Object> data = null;
				if(bytes != null) {
					data = (Map<String, Object>) SerializeUtil.deserialize(bytes);
				}
				String dataRowKey = null;
				Long tableVersion = null;
				if(data != null) {
					if(rowKeyList.size() > 0) {
						dataRowKey = getRowKeyVersion(tableKey, rowKeyList);
						if(dataRowKey.toString().equals(data.get("rowV").toString())) {
							return data.get("data");
						}
					}else{
						tableVersion = getTableVersion(tableKey);
						if(tableVersion.equals(Long.valueOf(data.get("tableV").toString()))) {
							return data.get("data");
						}
					}
					data = null;
				}
				if(data == null) {
					result = joinPoint.proceed();
					if(tableVersion == null) {
						tableVersion = getTableVersion(tableKey);
					}
					if(dataRowKey == null) {
						dataRowKey = getRowKeyVersion(tableKey, rowKeyList);
					}
					data = new HashMap<String, Object>();
					data.put("data", result);
					data.put("tableV", tableVersion);
					data.put("rowV", dataRowKey == null ? "" : dataRowKey.toString());
					if (ECCache.expire() > 0) {
						jedisCluster.setex(dataKey, ECCache.expire(), SerializeUtil.serialize(data));
					}else{
						jedisCluster.set(dataKey, SerializeUtil.serialize(data));
					}
				}
				break execute;
			}
			if (ECCache.type() == CACHETYPE.UPDATE
					|| ECCache.type() == CACHETYPE.DELETE
					|| ECCache.type() == CACHETYPE.ADD) {
				result = joinPoint.proceed();
				Class<?> returnType = method.getReturnType();
				if(returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
					if((Boolean)result == false) {
						break execute;
					}
				}
				if(tableKey != null) {//重置版本号
					updateTableVersion(tableKey);
				}
				for (String rowKey : rowKeyList) {
					updateTableRowVersion(tableKey, rowKey.getBytes());
				}
				break execute;
			}
		}
		return result;
	}

	private byte[] getCacheKey(ECCache ECCache, Method method, Object[] arguments) throws Exception {
		String dataKey = ECCache.getClass().getName() + "." + method.getName() + "." + Arrays.toString(SerializeUtil.serialize(arguments));
		dataKey = MD5Util.hex16T62(MD5Util.MD5(dataKey));
		return dataKey.getBytes();
	}

	
	private String getRowKeyVersion(byte[] tableKey, Set<String> rowKeyList) throws Exception {
		StringBuilder dataRowKey = new StringBuilder();
		for (String rowKey : rowKeyList) {
			long rowVersion = getTableRowVersion(tableKey, rowKey.getBytes());
			if(dataRowKey.length() > 0) {
				dataRowKey.append(";");
			}
			dataRowKey.append(rowKey + "=" + rowVersion);
		}
		return dataRowKey.toString();
	}
	
	private byte[] tableKey = "tableVersion".getBytes();
	
	public Long getTableVersion(byte[] tableKey) throws Exception {
		byte[] version = jedisCluster.hget(tableKey, this.tableKey);
		if(version == null) {
			version = SerializeUtil.serialize(System.currentTimeMillis());
			updateTableVersion(tableKey, version);
		}
		return (Long) SerializeUtil.deserialize(version);
	}
	
	public Long getTableRowVersion(byte[] tableKey, byte[] rowKey) throws Exception {
		byte[] version = jedisCluster.hget(tableKey, rowKey);
		if(version == null) {
			version = SerializeUtil.serialize(System.currentTimeMillis());
			updateTableRowVersion(tableKey, rowKey, version);
		}
		return (Long) SerializeUtil.deserialize(version);
	}
	
	public long updateTableVersion(byte[] tableKey) throws Exception {
		byte[] version = SerializeUtil.serialize(System.currentTimeMillis());
		return updateTableVersion(tableKey, version);
	}
	
	public long updateTableVersion(byte[] tableKey, byte[] version) throws Exception {
		jedisCluster.hset(tableKey, this.tableKey, version);
		return (Long) SerializeUtil.deserialize(version);
	}
	
	public long updateTableRowVersion(byte[] tableKey, byte[] rowKey) throws Exception {
		byte[] version = SerializeUtil.serialize(System.currentTimeMillis());
		return updateTableRowVersion(tableKey, rowKey, version);
	}
	
	public long updateTableRowVersion(byte[] tableKey, byte[] rowKey, byte[] version) throws Exception {
		jedisCluster.hset(tableKey, rowKey, version);
		return (Long) SerializeUtil.deserialize(version);
	}
	
	@Override
	public int getOrder() {
		return 1;
	}

}