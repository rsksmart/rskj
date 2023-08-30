package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.BlockRef;
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

    private static final List<String> IDENTIFIERS = Arrays.asList("earliest", "latest", "pending");
    private static final List<String> BLOCK_INPUT_KEYS = Arrays.asList("blockHash", "blockNumber");

    private final String identifier;
    private final Map<String, String> inputs;

    public BlockRefParam(BlockRef blockRef) {
        if(blockRef.getIdentifier() != null) {
            validateString(blockRef.getIdentifier());
        } else {
            validateMap(blockRef.getInputs());
        }

        this.identifier = blockRef.getIdentifier();
        this.inputs = blockRef.getInputs();
    }

    private void validateString(String identifier) {
        if(!IDENTIFIERS.contains(identifier)
                && !Utils.isDecimalString(identifier)
                && !Utils.isHexadecimalString(identifier)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block identifier '" + identifier + "'");
        }
    }

    private void validateMap(Map<String, String> inputs) {
        if(!inputs.containsKey(BLOCK_INPUT_KEYS.get(0))
        && !inputs.containsKey(BLOCK_INPUT_KEYS.get(1))) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block input");
        }
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
            BlockRef blockRef = mapper.readValue(jp, BlockRef.class);
            return new BlockRefParam(blockRef);
        }
    }
}
