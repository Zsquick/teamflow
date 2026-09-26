package com.teamflow.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要记录操作日志的业务方法。 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String resourceType();

    /**
     * 从方法参数读取资源编号的受限表达式。
     *
     * <p>只允许 {@code #参数名} 或 {@code #参数名.只读属性链}，不允许调用
     * 方法、访问 Spring Bean、引用类型或执行运算。</p>
     */
    String resourceId() default "";
}
