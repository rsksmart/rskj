package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;

@JsonDeserialize(using = HexNumberParam.Deserializer.class)
public class HexNumberParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String hexNumber;

    public HexNumberParam(String hexNumber) {
        boolean hasPrefix = HexUtils.hasHexPrefix(hexNumber);
        if (!HexUtils.isHex(hexNumber.toLowerCase(), hasPrefix ? 2 : 0)) {
            try {
                new BigInteger(hexNumber);
            } catch(Exception e) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid param " + hexNumber + ": value must be a valid hex or string number.");
            }
        }

        this.hexNumber = hexNumber;
    }

    public String getHexNumber() {
        return this.hexNumber;
    }

    public static class Deserializer extends StdDeserializer<HexNumberParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexNumberParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexNumber = jp.getText();
            return new HexNumberParam(hexNumber);
        }
    }
}
