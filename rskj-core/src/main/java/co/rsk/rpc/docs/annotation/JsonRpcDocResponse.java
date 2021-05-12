package co.rsk.rpc.docs.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * While we could have put this on the method parameters,
 * it actively made the method parameter
 */
@Retention(RetentionPolicy.SOURCE)
public @interface JsonRpcDocResponse {

    /**
     * The description or file and path of the response
     */
    String description() default "";

    /**
     * Specify if the description should be loaded from the yml file
     * if true the description attribute should be a path
     */
    boolean loadDescriptionFromFile() default false;

    /**
     * Either specify "Success" or the error code
     */
    String code() default "";

    /**
     * Specify the yaml path which contains the example
     */
    String examplePath() default "";

    /**
     * Specify if the model should be added to the model documetnation
     * and a hyperlink should be added
     */
    boolean attachModel() default false;

    /**
     * Specify if this is a success response
     */
    boolean success() default true;

}
