package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexDataParam.Deserializer.class)
public class HexDataParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private final byte[] rawDataBytes;

    public HexDataParam(String rawData){
        try {
            this.rawDataBytes = HexUtils.stringHexToByteArray(rawData);
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid data format. " + e.getMessage());
        }
    }

    public byte[] getRawDataBytes() {
        return rawDataBytes;
    }

    public static class Deserializer extends StdDeserializer<HexDataParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexDataParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexRawData = jp.getText();
            return new HexDataParam(hexRawData);
        }
    }
}
