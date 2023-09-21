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

import java.io.IOException;

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

    public CallArgumentsParam(HexAddressParam from, HexAddressParam to, HexNumberParam gas,
                              HexNumberParam gasPrice, HexNumberParam gasLimit, HexNumberParam nonce,
                              HexNumberParam chainId, HexNumberParam value, HexDataParam data) {
        this.from = from;
        this.to = to;
        this.gas = gas;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.nonce = nonce;
        this.chainId = chainId;
        this.value = value;
        this.data = data;
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

        CallArguments callArguments = new CallArguments();
        callArguments.setFrom(caFrom);
        callArguments.setTo(caTo);
        callArguments.setGas(caGas);
        callArguments.setGasPrice(caGasPrice);
        callArguments.setGasLimit(caGasLimit);
        callArguments.setNonce(caNonce);
        callArguments.setChainId(caChainId);
        callArguments.setValue(caValue);
        callArguments.setData(caData);

        return callArguments;
    }

    public static class Deserializer extends StdDeserializer<CallArgumentsParam> {
        private static final long serialVersionUID = 1L;

        public Deserializer() { this(null); }

        public Deserializer(Class<?> vc) { super(vc); }

        @Override
        public CallArgumentsParam deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode node =  jp.getCodec().readTree(jp);

            BlockIdentifierParam fromBlock = node.has("fromBlock") ? new BlockIdentifierParam(node.get("fromBlock").asText()) : null;

            HexAddressParam from = node.has("from") ? new HexAddressParam(node.get("from").asText()) : null;
            HexAddressParam to = node.has("to") ? new HexAddressParam(node.get("to").asText()) : null;
            HexNumberParam gas = node.has("gas") ? new HexNumberParam(node.get("gas").asText()) : null;
            HexNumberParam gasPrice = node.has("gasPrice") ? new HexNumberParam(node.get("gasPrice").asText()) : null;
            HexNumberParam gasLimit = node.has("gasLimit") ? new HexNumberParam(node.get("gasLimit").asText()) : null;
            HexNumberParam nonce = node.has("nonce") ? new HexNumberParam(node.get("nonce").asText()) : null;
            HexNumberParam chainId = node.has("chainId") ? new HexNumberParam(node.get("chainId").asText()) : null;
            HexNumberParam value = node.has("value") ? new HexNumberParam(node.get("value").asText()) : null;
            HexDataParam data = node.has("data") ? new HexDataParam(node.get("data").asText()) : null;

            return new CallArgumentsParam(from, to, gas, gasPrice, gasLimit, nonce, chainId, value, data);
        }
    }
}
