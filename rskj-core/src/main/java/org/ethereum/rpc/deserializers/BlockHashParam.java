package org.ethereum.rpc.deserializers;

import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;

import static co.rsk.util.HexUtils.stringHexToByteArray;

@JsonDeserialize(using = BlockHashParam.Deserializer.class)
public class BlockHashParam {
    private final Keccak256 blockHash;

    public BlockHashParam(String hash) {
        // TODO: validate
        this.blockHash = new Keccak256(stringHexToByteArray(hash));
    }

    public static class Deserializer extends StdDeserializer<BlockHashParam> {
        private static final int BLOCK_HASH_BYTE_LENGTH = 32;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public BlockHashParam deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            String blockHashString = node.asText();
            byte[] blockHashBytes;

            try {
                blockHashBytes = HexUtils.stringHexToByteArray(blockHashString);
            } catch (Exception e) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid block hash format. " + e.getMessage());
            }

            if (BLOCK_HASH_BYTE_LENGTH != blockHashBytes.length) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid block hash: incorrect length.");
            }
            return new BlockHashParam(blockHashString);
        }
    }

    public Keccak256 getBlockHash() {
        return blockHash;
    }
}
