package com.kyexpress.ec.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheKey {

	boolean required() default true;

	String[] keys();

	KEYTYPE type() default KEYTYPE.OR;

	public enum KEYTYPE {
		ADD, OR;
	}
	
}
