package com.wuhao.sc.cache;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ECCacheParam {
    boolean required() default true;

    public String value();

    public Class<?> clazz() default void.class;

}
