package org.ethereum.rpc.parameters;

import co.rsk.core.RskAddress;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.ethereum.rpc.CallArguments;

import java.io.IOException;
import java.util.Optional;

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
        String caFrom = Optional.ofNullable(this.from)
                .map(HexAddressParam::getAddress)
                .map(RskAddress::toJsonString)
                .orElse(null);
        String caTo = Optional.ofNullable(this.to)
                .map(HexAddressParam::getAddress)
                .map(RskAddress::toJsonString)
                .orElse(null);
        String caGas = Optional.ofNullable(this.gas)
                .map(HexNumberParam::getHexNumber)
                .orElse(null);
        String caGasPrice = Optional.ofNullable(this.gasPrice)
                .map(HexNumberParam::getHexNumber)
                .orElse(null);
        String caGasLimit = Optional.ofNullable(this.gasLimit)
                .map(HexNumberParam::getHexNumber)
                .orElse(null);
        String caNonce = Optional.ofNullable(this.nonce)
                .map(HexNumberParam::getHexNumber)
                .orElse(null);
        String caChainId = Optional.ofNullable(this.chainId)
                .map(HexNumberParam::getHexNumber)
                .orElse(null);
        String caValue = Optional.ofNullable(this.value)
                .map(HexNumberParam::getHexNumber)
                .orElse(null);
        String caData = Optional.ofNullable(this.data)
                .map(HexDataParam::getAsHexString)
                .orElse(null);

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

            HexAddressParam from = Optional.ofNullable(node)
                    .map(n -> n.get("from"))
                    .map(JsonNode::asText)
                    .map(HexAddressParam::new)
                    .orElse(null);
            HexAddressParam to = Optional.ofNullable(node)
                    .map(n -> n.get("to"))
                    .map(JsonNode::asText)
                    .map(HexAddressParam::new)
                    .orElse(null);
            HexNumberParam gas = Optional.ofNullable(node)
                    .map(n -> n.get("gas"))
                    .map(JsonNode::asText)
                    .map(HexNumberParam::new)
                    .orElse(null);
            HexNumberParam gasPrice = Optional.ofNullable(node)
                    .map(n -> n.get("gasPrice"))
                    .map(JsonNode::asText)
                    .map(HexNumberParam::new)
                    .orElse(null);
            HexNumberParam gasLimit = Optional.ofNullable(node)
                    .map(n -> n.get("gasLimit"))
                    .map(JsonNode::asText)
                    .map(HexNumberParam::new)
                    .orElse(null);
            HexNumberParam nonce = Optional.ofNullable(node)
                    .map(n -> n.get("nonce"))
                    .map(JsonNode::asText)
                    .map(HexNumberParam::new)
                    .orElse(null);
            HexNumberParam chainId = Optional.ofNullable(node)
                    .map(n -> n.get("chainId"))
                    .map(JsonNode::asText)
                    .map(HexNumberParam::new)
                    .orElse(null);
            HexNumberParam value = Optional.ofNullable(node)
                    .map(n -> n.get("value"))
                    .map(JsonNode::asText)
                    .map(HexNumberParam::new)
                    .orElse(null);
            HexDataParam data = Optional.ofNullable(node)
                    .map(n -> n.get("data"))
                    .map(JsonNode::asText)
                    .map(HexDataParam::new)
                    .orElse(null);

            return new CallArgumentsParam(from, to, gas, gasPrice, gasLimit, nonce, chainId, value, data);
        }
    }
}
