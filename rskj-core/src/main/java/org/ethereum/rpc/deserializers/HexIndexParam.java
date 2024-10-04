package org.ethereum.rpc.deserializers;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;

import static co.rsk.util.HexUtils.jsonHexToInt;

public class HexIndexParam {
    private final Integer index;

    public HexIndexParam(String index) {
        this.index = jsonHexToInt(index);
    }

    public static class Deserializer extends StdDeserializer<HexIndexParam> {

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public HexIndexParam deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            String indexString = node.asText();

            if(!HexUtils.hasHexPrefix(indexString)) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid argument: " + indexString + ": param should be a hex value string.");
            }

            return new HexIndexParam(indexString);
        }


    }
}
