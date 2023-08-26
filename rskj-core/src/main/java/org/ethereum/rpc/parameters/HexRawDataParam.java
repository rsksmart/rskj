package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;
import java.io.Serializable;

@JsonDeserialize(using = HexRawDataParam.Deserializer.class)
public class HexRawDataParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final int HASH_BYTE_LENGTH = 148;

    private final byte[] rawDataBytes;

    public HexRawDataParam(String rawData){
        try {
            this.rawDataBytes = HexUtils.stringHexToByteArray(rawData);
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid data format. " + e.getMessage());
        }

        if (HASH_BYTE_LENGTH != rawDataBytes.length) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid data: incorrect length.");
        }
    }

    public byte[] getRawDataBytes() {
        return rawDataBytes;
    }

    public static class Deserializer extends StdDeserializer<HexRawDataParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public HexRawDataParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String hexRawData = jp.getText();
            return new HexRawDataParam(hexRawData);
        }
    }
}
