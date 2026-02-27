/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.CallArguments;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Function;

@JsonDeserialize(using = CallArgumentsParam.Deserializer.class)
public class CallArgumentsParam {

    private final HexAddressParam from;
    private final HexAddressParam to;
    private final HexNumberParam gas;
    private final HexNumberParam gasPrice;
    private final HexNumberParam gasLimit;
    private final HexNumberParam nonce;
    private final HexNumberParam chainId;
    private final HexNumberParam value;
    private final HexDataParam data;
    private final HexDataParam input;
    private final HexNumberParam type;
    private final HexNumberParam rskSubtype;

    public CallArgumentsParam(HexAddressParam from, HexAddressParam to, HexNumberParam gas,
                              HexNumberParam gasPrice, HexNumberParam gasLimit, HexNumberParam nonce,
                              HexNumberParam chainId, HexNumberParam value, HexDataParam data, HexDataParam input,
                              HexNumberParam type, HexNumberParam rskSubtype) {
        this.from = from;
        this.to = to;
        this.gas = gas;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.nonce = nonce;
        this.chainId = chainId;
        this.value = value;
        this.data = data;
        this.input = input;
        this.type = type;
        this.rskSubtype = rskSubtype;
    }

    public HexAddressParam getFrom() {
        return from;
    }

    public HexAddressParam getTo() {
        return to;
    }

    public HexNumberParam getGas() {
        return gas;
    }

    public HexNumberParam getGasPrice() {
        return gasPrice;
    }

    public HexNumberParam getGasLimit() {
        return gasLimit;
    }

    public HexNumberParam getNonce() {
        return nonce;
    }

    public HexNumberParam getChainId() {
        return chainId;
    }

    public HexNumberParam getValue() {
        return value;
    }

    public HexDataParam getData() {
        return data;
    }

    public HexDataParam getInput() {
        return input;
    }

    public HexNumberParam getType() {
        return type;
    }

    public HexNumberParam getRskSubtype() {
        return rskSubtype;
    }

    public CallArguments toCallArguments() {
        String caFrom = this.from == null ? null : this.from.getAddress().toJsonString();
        String caTo = this.to == null ? null : this.to.getAddress().toJsonString();
        String caGas = this.gas == null ? null : this.gas.getHexNumber();
        String caGasPrice = this.gasPrice == null ? null : this.gasPrice.getHexNumber();
        String caGasLimit = this.gasLimit == null ? null : this.gasLimit.getHexNumber();
        String caNonce = this.nonce == null ? null : this.nonce.getHexNumber();
        String caChainId = this.chainId == null ? null : this.chainId.getHexNumber();
        String caValue = this.value == null ? null : this.value.getHexNumber();
        String caData = this.data == null ? null : this.data.getAsHexString();
        String caInput = this.input == null ? null : this.input.getAsHexString();
        String caType = this.type == null ? null : this.type.getHexNumber();
        String caRskSubtype = this.rskSubtype == null ? null : this.rskSubtype.getHexNumber();

        CallArguments callArguments = new CallArguments();
        callArguments.setFrom(caFrom);
        callArguments.setTo(caTo);
        callArguments.setGas(caGas);
        callArguments.setGasPrice(caGasPrice);
        callArguments.setGasLimit(caGasLimit);
        callArguments.setNonce(caNonce);
        callArguments.setChainId(caChainId);
        callArguments.setValue(caValue);
        if (caData != null) {
            callArguments.setData(caData);
        }
        if (caInput != null) {
            callArguments.setInput(caInput);
        }
        callArguments.setType(caType);
        callArguments.setRskSubtype(caRskSubtype);

        return callArguments;
    }

    @Override
    public String toString() {
        return "CallArguments{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", gas='" + gas + '\'' +
                ", gasLimit='" + gasLimit + '\'' +
                ", gasPrice='" + gasPrice + '\'' +
                ", value='" + value + '\'' +
                ", data='" + (data != null ? data.getAsHexString() : null) + '\'' +
                ", nonce='" + nonce + '\'' +
                ", chainId='" + chainId + '\'' +
                ", type='" + type + '\'' +
                ", rskSubtype='" + rskSubtype + '\'' +
                '}';
    }

    public static class Deserializer extends StdDeserializer<CallArgumentsParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public CallArgumentsParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node =  jp.getCodec().readTree(jp);

            HexAddressParam from = paramOrNull(node, "from", HexAddressParam::new);
            HexAddressParam to = paramOrNull(node, "to", HexAddressParam::new);
            HexNumberParam gas = paramOrNull(node, "gas", HexNumberParam::new);
            HexNumberParam gasPrice = paramOrNull(node, "gasPrice", HexNumberParam::new);
            HexNumberParam gasLimit = paramOrNull(node, "gasLimit", HexNumberParam::new);
            HexNumberParam nonce = paramOrNull(node, "nonce", HexNumberParam::new);
            HexNumberParam chainId = paramOrNull(node, "chainId", HexNumberParam::new);
            HexNumberParam value = paramOrNull(node, "value", HexNumberParam::new);
            HexDataParam data = paramOrNull(node, "data", HexDataParam::new);
            HexDataParam input = paramOrNull(node, "input", HexDataParam::new);
            HexNumberParam type = paramOrNull(node, "type", HexNumberParam::new);
            HexNumberParam rskSubtype = paramOrNull(node, "rskSubtype", HexNumberParam::new);

            return new CallArgumentsParam(from, to, gas, gasPrice, gasLimit, nonce, chainId, value, data, input, type, rskSubtype);
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
