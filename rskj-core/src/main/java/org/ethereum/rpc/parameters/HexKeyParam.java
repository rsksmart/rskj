package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexKeyParam.Deserializer.class)
public class HexKeyParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String hexKey;

    public HexKeyParam(String hexKey) {
        if (hexKey == null || hexKey.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid key: empty or null.");
        }

        boolean hasPrefix = HexUtils.hasHexPrefix(hexKey);
        if (!HexUtils.isHex(hexKey.toLowerCase(), hasPrefix ? 2 : 0)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid param " + hexKey + ": value must be a valid hex.");
        }

        this.hexKey = hexKey;
    }

    public String getHexKey() {
        return hexKey;
    }

    public static class Deserializer extends StdDeserializer<HexKeyParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexKeyParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexKey = jp.getText();
            return new HexKeyParam(hexKey);
        }
    }
}
