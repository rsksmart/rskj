package org.ethereum.rpc.exception;

/**
 * Created by mario on 17/10/2016.
 */
public class RskJsonRpcRequestException extends RuntimeException{

    private final Integer code;

    protected RskJsonRpcRequestException(Integer code, String message, Exception e) {
        super(message, e);
        this.code = code;
    }

    public RskJsonRpcRequestException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

}
