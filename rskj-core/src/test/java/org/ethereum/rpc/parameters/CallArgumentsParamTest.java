package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallArgumentsParamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String FROM = "0x7986b3df570230288501eea3d890bd66948c9b79";
    private final String TO = "0xe7b8e91401bf4d1669f54dc5f98109d7efbc4eea";
    private final String GAS = "0x76c0";
    private final String GAS_PRICE = "0x9184e72a000";
    private final String VALUE = "0x9184e72a";
    private final String DATA = "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675";
    private final String NONCE = "0x1";
    private final String CHAIN_ID = "0x539";

    @Test
    public void testValidCallArgumentsParam() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"from\": \"" + FROM + "\"," +
                "            \"to\" : \"" + TO + "\"," +
                "            \"gas\": \"" + GAS + "\"," +
                "            \"gasPrice\":\"" + GAS_PRICE + "\"," +
                "            \"value\":\"" + VALUE + "\"," +
                "            \"data\": \"" + DATA + "\", " +
                "            \"nonce\": \"" + NONCE + "\", " +
                "            \"chainId\": \"" + CHAIN_ID + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNotNull(callArgumentsParam);

        assertEquals(FROM, callArgumentsParam.getFrom().getAddress().toJsonString());
        assertEquals(TO, callArgumentsParam.getTo().getAddress().toJsonString());
        assertEquals(GAS, callArgumentsParam.getGas().getHexNumber());
        assertEquals(GAS_PRICE, callArgumentsParam.getGasPrice().getHexNumber());
        assertEquals(VALUE, callArgumentsParam.getValue().getHexNumber());
        assertEquals(DATA, callArgumentsParam.getData().getAsHexString());
        assertEquals(NONCE, callArgumentsParam.getNonce().getHexNumber());
        assertEquals(CHAIN_ID, callArgumentsParam.getChainId().getHexNumber());
    }

    @Test
    public void testInvalidFromInCallArgumentsParam() throws JsonProcessingException {
        String from = "0x7986b3df570230288501eea3d890bd66948c9b7s";

        String callArgumentsInput = "{\n" +
                "            \"from\": \"" + from + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidToInCallArgumentsParam() throws JsonProcessingException {
        String to = "0xe7b8e91401bf4d1669f54dc5f98109d7efbc4esw";

        String callArgumentsInput = "{\n" +
                "            \"to\": \"" + to + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidGasInCallArgumentsParam() throws JsonProcessingException {
        String gas = "0x76cz";

        String callArgumentsInput = "{\n" +
                "            \"gas\": \"" + gas + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidGasPriceInCallArgumentsParam() throws JsonProcessingException {
        String gasPrice = "0x9184e72a0zq";

        String callArgumentsInput = "{\n" +
                "            \"gasPrice\": \"" + gasPrice + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidValueInCallArgumentsParam() throws JsonProcessingException {
        String value = "0x9184e7tq";

        String callArgumentsInput = "{\n" +
                "            \"value\": \"" + value + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidDataInCallArgumentsParam() throws JsonProcessingException {
        String data = "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f0724456pl";

        String callArgumentsInput = "{\n" +
                "            \"data\": \"" + data + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidNonceInCallArgumentsParam() throws JsonProcessingException {
        String nonce = "0xj";

        String callArgumentsInput = "{\n" +
                "            \"nonce\": \"" + nonce + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testInvalidChainIdInCallArgumentsParam() throws JsonProcessingException {
        String chainId = "0xb2r";

        String callArgumentsInput = "{\n" +
                "            \"chainId\": \"" + chainId + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);

        assertThrows(RskJsonRpcRequestException.class, () -> objectMapper.convertValue(jsonNode, CallArgumentsParam.class));
    }

    @Test
    public void testToCallArguments() {
        CallArgumentsParam callArgumentsParam = new CallArgumentsParam(
                new HexAddressParam(FROM),
                new HexAddressParam(TO),
                new HexNumberParam(GAS),
                new HexNumberParam(GAS_PRICE),
                null,
                new HexNumberParam(NONCE),
                new HexNumberParam(CHAIN_ID),
                new HexNumberParam(VALUE),
                new HexDataParam(DATA)
        );

        CallArguments callArguments = callArgumentsParam.toCallArguments();

        assertEquals(FROM, callArguments.getFrom());
        assertEquals(TO, callArguments.getTo());
        assertEquals(GAS, callArguments.getGas());
        assertEquals(GAS_PRICE, callArguments.getGasPrice());
        assertEquals(NONCE, callArguments.getNonce());
        assertEquals(CHAIN_ID, callArguments.getChainId());
        assertEquals(VALUE, callArguments.getValue());
        assertEquals(DATA, callArguments.getData());
    }
}
