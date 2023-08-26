package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.Utils;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@JsonDeserialize(using = BlockIdentifierParam.Deserializer.class)
public class BlockIdentifierParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final List<String> STRING_IDENTIFIERS = Arrays.asList("earliest", "latest", "pending");

    private final String identifier;

    public BlockIdentifierParam(String identifier) {
        if(!STRING_IDENTIFIERS.contains(identifier)
            && !Utils.isDecimalString(identifier)
            && !Utils.isHexadecimalString(identifier)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier '" + identifier + "'");
        }

        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public static class Deserializer extends StdDeserializer<BlockIdentifierParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public BlockIdentifierParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String identifier = jp.getText();
            return new BlockIdentifierParam(identifier);
        }
    }
}
