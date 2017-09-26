package com.kyexpress.ec.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface ECCache {

	boolean required() default true;
	
	public Class<?>[] value() default {}; // 缓存key

	public int expire() default 3600; // 缓存多少秒

	public boolean skip() default false; //跳过缓存
}