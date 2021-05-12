package co.rsk.rpc.docs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface JsonRpcDoc {

    /**
     * This parameter is used to set if a method is write or read
     * We do not set a default value, forcing the user to evaluate if it is as such
     */
    boolean isWriteMethod();

    /**
     * This states in a case of overriden functions which overloads details
     * should be considered the base documentation
     */
    boolean primaryForOverloads() default false;

    /**
     * The description of the method
     */
    String description() default "";

    /**
     * A shorthand description of the method.
     * When this is not specified, description will be automatically substituted
     */
    String summary() default "";

    /**
     * Sometimes when a model cannot be inferred we can explicitly set a return model string
     */
    String returnModel() default "";

    String[] requestExamples() default "";

    /**
     * Specify the request params in the method if any
     */
    JsonRpcDocRequestParameter[] requestParams() default {};

    /**
     * Specify the responses in the method if any
     */
    JsonRpcDocResponse[] responses() default {};


}
