package org.ethereum.rpc.deserializers;

import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.io.IOException;

import static co.rsk.util.HexUtils.stringHexToByteArray;

public class TransactionHashParam {
    private final Keccak256 transactionHash;

    public TransactionHashParam(String hash) {
        this.transactionHash = new Keccak256(stringHexToByteArray(hash));
    }

    public static class Deserializer extends StdDeserializer<TransactionHashParam> {
        private static final int TRANSACTION_HASH_BYTE_LENGTH = 32;

        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public TransactionHashParam deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            String transactionHashString = node.asText();
            byte[] transactionHashBytes;

            try {
                transactionHashBytes = HexUtils.stringHexToByteArray(transactionHashString);
            } catch (Exception e) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid transaction hash format. " + e.getMessage());
            }

            if (TRANSACTION_HASH_BYTE_LENGTH != transactionHashBytes.length) {
                throw RskJsonRpcRequestException.invalidParamError("Invalid transaction hash: incorrect length.");
            }
            return new TransactionHashParam(transactionHashString);
        }


    }

    public Keccak256 getTransactionHash() {
        return transactionHash;
    }
}
