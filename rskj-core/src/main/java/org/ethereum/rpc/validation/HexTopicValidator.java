package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.util.regex.Pattern;

public class HexTopicValidator {
    private static final String HEX_TOPIC_REGEX = "^0x[a-fA-F0-9]{64}$";
    private static final Pattern HEX_TOPIC_PATTERN = Pattern.compile(HEX_TOPIC_REGEX);

    public static boolean isValid(String topic){
        if(!HEX_TOPIC_PATTERN.matcher(topic).matches()){
            throw RskJsonRpcRequestException.invalidParamError("Invalid topic: " + topic);
        }
        return true;
    }

}
