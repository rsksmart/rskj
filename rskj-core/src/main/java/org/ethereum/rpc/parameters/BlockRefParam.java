package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.Utils;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@JsonDeserialize(using = BlockRefParam.Deserializer.class)
public class BlockRefParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String REQUIRED_CANONICAL_KEY = "requireCanonical";
    private static final String BLOCK_HASH_KEY = "blockHash";
    private static final String BLOCK_NUMBER_KEY = "blockNumber";
    private static final List<String> BLOCK_INPUT_KEYS_TO_VALIDATE = Arrays.asList(BLOCK_HASH_KEY, BLOCK_NUMBER_KEY);
    private static final List<String> IDENTIFIERS_TO_VALIDATE = Arrays.asList("earliest", "latest", "pending");

    private String identifier;
    private Map<String, String> inputs;

    public BlockRefParam(String identifier) {
        if(!IDENTIFIERS_TO_VALIDATE.contains(identifier)
                && !Utils.isDecimalString(identifier)
                && !Utils.isHexadecimalString(identifier)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier '" + identifier + "'");
        }

        this.identifier = identifier;
    }

    public BlockRefParam(Map<String, String> inputs) {
        if(inputs.keySet().stream().noneMatch(BLOCK_INPUT_KEYS_TO_VALIDATE::contains)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block input");
        }

        validateMapItems(inputs);

        this.inputs = inputs;
    }

    private void validateMapItems(Map<String, String> inputs) {
        inputs.forEach((key, value) -> {
            switch (key) {
                case REQUIRED_CANONICAL_KEY:
                    if(!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw RskJsonRpcRequestException.invalidParamError(String
                                .format("Invalid input: %s must be a String \"true\" or \"false\"", REQUIRED_CANONICAL_KEY));
                    }
                    break;
                case BLOCK_HASH_KEY:
                    new BlockHashParam(value);
                    break;
                case BLOCK_NUMBER_KEY:
                    new HexNumberParam(value);
                    break;
            }
        });
    }

    public String getIdentifier() {
        return identifier;
    }

    public Map<String, String> getInputs() {
        return inputs;
    }

    public static class Deserializer extends StdDeserializer<BlockRefParam> {
        private static final long serialVersionUID = 1L;
        private final ObjectMapper mapper = new ObjectMapper();

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public BlockRefParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            JsonNodeType nodeType = node.getNodeType();

            if(nodeType == JsonNodeType.STRING) {
                return new BlockRefParam(node.asText());
            } else if(nodeType == JsonNodeType.OBJECT) {
                Map<String, String> inputs = mapper.convertValue(node, Map.class);
                return new BlockRefParam(inputs);
            } else {
                throw RskJsonRpcRequestException.invalidParamError("Invalid input");
            }
        }
    }
}
