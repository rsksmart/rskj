package co.rsk.rpc.docs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface JsonRpcDocModelType {

    /**
     * Use this if you want to set a custom type on the doc
     * Useful when on the code something like "Object" is set
     * however that actually maens String | Integer
     */
    String documentationType() default "";

    /**
     * Use this if you want to process another class
     * and add it to the documentation as a result of processing
     * this entry
     */
    String[] processClassNames() default {};

}
