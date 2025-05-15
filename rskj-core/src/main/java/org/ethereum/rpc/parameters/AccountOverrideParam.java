package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Function;

@JsonDeserialize(using = AccountOverrideParam.Deserializer.class)
public class AccountOverrideParam {

    private final HexNumberParam balance;

    public AccountOverrideParam( HexNumberParam balance) {
        this.balance = balance;
    }

    public HexNumberParam getBalance() {
        return balance;
    }

    public static class Deserializer extends StdDeserializer<AccountOverrideParam> {

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public AccountOverrideParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node =  jp.getCodec().readTree(jp);

            HexNumberParam balance = paramOrNull(node, "balance", HexNumberParam::new);
            return new AccountOverrideParam(balance);
        }

        @Nullable
        private static <T> T paramOrNull(JsonNode node, String fieldName, Function<String, T> paramFactory) {
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode == null || fieldNode.isNull()) {
                return null;
            }

            return paramFactory.apply(fieldNode.asText());
        }
    }

}
