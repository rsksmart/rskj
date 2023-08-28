package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;

@JsonDeserialize(using = CallArgumentsParam.Deserializer.class)
public class CallArgumentsParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient final CallArguments callArguments;

    public CallArgumentsParam(CallArguments callArguments) {
        validateAddressProperty(callArguments.getFrom(), "from");
        validateAddressProperty(callArguments.getTo(), "to");
        validateNumberProperty(callArguments.getGas(), "gas");
        validateNumberProperty(callArguments.getGasPrice(), "gasPrice");
        validateNumberProperty(callArguments.getGasLimit(), "gasLimit");
        validateNumberProperty(callArguments.getNonce(), "nonce");
        validateNumberProperty(callArguments.getChainId(), "chainId");
        validateNumberProperty(callArguments.getValue(), "value");
        validateDataProperty(callArguments.getData());

        this.callArguments = callArguments;
    }

    public CallArguments getCallArguments() {
        return callArguments;
    }

    private boolean isPropertyValidHex(String propertyValue) {
        boolean hasPrefix = HexUtils.hasHexPrefix(propertyValue);
        return HexUtils.isHex(propertyValue.toLowerCase(), hasPrefix ? 2 : 0);
    }

    private void validateAddressProperty(String propertyValue, String propertyName) {
        if (propertyValue == null || propertyValue.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid param " + propertyName + ": empty or null.");
        }

        if (!isPropertyValidHex(propertyValue)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid param " + propertyName + ": invalid character in hex input.");
        }
    }

    private void validateNumberProperty(String propertyValue, String propertyName) {
        if(propertyValue == null) {
            return;
        }

        if (!isPropertyValidHex(propertyValue)) {
            try {
                new BigInteger(propertyValue);
            } catch(Exception e) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid param " + propertyName + ": value must be a valid hex or string number.");
            }
        }
    }

    private void validateDataProperty(String propertyValue) {
        if(propertyValue == null) {
            return;
        }

        if(!isPropertyValidHex(propertyValue)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid param data: invalid character in hex input.");
        }
    }

    public static class Deserializer extends StdDeserializer<CallArgumentsParam> {
        private static final long serialVersionUID = 1L;
        private final ObjectMapper mapper = new ObjectMapper();

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public CallArgumentsParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            CallArguments callArguments = mapper.readValue(jp, CallArguments.class);
            return new CallArgumentsParam(callArguments);
        }
    }
}
