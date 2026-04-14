/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CallArgumentsParamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String FROM = "0x7986b3df570230288501eea3d890bd66948c9b79";
    private static final String TO = "0xe7b8e91401bf4d1669f54dc5f98109d7efbc4eea";
    private static final String GAS = "0x76c0";
    private static final String GAS_PRICE = "0x9184e72a000";
    private static final String VALUE = "0x9184e72a";
    private static final String DATA = "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675";
    private static final String NONCE = "0x1";
    private static final String CHAIN_ID = "0x539";
    private static final String NULL = "null";

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
    public void testToCallArgumentsWithData() {
        CallArgumentsParam callArgumentsParam = new CallArgumentsParam(
                new HexAddressParam(FROM),
                new HexAddressParam(TO),
                new HexNumberParam(GAS),
                new HexNumberParam(GAS_PRICE),
                null,
                new HexNumberParam(NONCE),
                new HexNumberParam(CHAIN_ID),
                new HexNumberParam(VALUE),
                new HexDataParam(DATA),
                null,
                null,
                null
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
        assertEquals(DATA, callArguments.getInput());
    }

    @Test
    public void testToCallArgumentsWithInput() {
        CallArgumentsParam callArgumentsParam = new CallArgumentsParam(
                new HexAddressParam(FROM),
                new HexAddressParam(TO),
                new HexNumberParam(GAS),
                new HexNumberParam(GAS_PRICE),
                null,
                new HexNumberParam(NONCE),
                new HexNumberParam(CHAIN_ID),
                new HexNumberParam(VALUE),
                null,
                new HexDataParam(DATA),
                null,
                null
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
        assertEquals(DATA, callArguments.getInput());
    }

    @Test
    public void testNullCallArgumentsParams() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"from\": " + NULL + ", " +
                "            \"to\" : " + NULL + ", " +
                "            \"gas\": " + NULL + ", " +
                "            \"gasPrice\": " + NULL + ", " +
                "            \"value\": " + NULL + ", " +
                "            \"data\": " + NULL + ", " +
                "            \"nonce\": " + NULL + ", " +
                "            \"chainId\": " + NULL + ", " +
                "            \"type\": " + NULL + ", " +
                "            \"rskSubtype\": " + NULL + "}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNotNull(callArgumentsParam);

        assertNull(callArgumentsParam.getFrom());
        assertNull(callArgumentsParam.getTo());
        assertNull(callArgumentsParam.getGas());
        assertNull(callArgumentsParam.getGasPrice());
        assertNull(callArgumentsParam.getValue());
        assertNull(callArgumentsParam.getData());
        assertNull(callArgumentsParam.getNonce());
        assertNull(callArgumentsParam.getChainId());
        assertNull(callArgumentsParam.getType());
        assertNull(callArgumentsParam.getRskSubtype());
    }

    @Test
    public void testNoCallArgumentsParams() throws JsonProcessingException {
        String callArgumentsInput = "{ }";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNotNull(callArgumentsParam);

        assertNull(callArgumentsParam.getFrom());
        assertNull(callArgumentsParam.getTo());
        assertNull(callArgumentsParam.getGas());
        assertNull(callArgumentsParam.getGasPrice());
        assertNull(callArgumentsParam.getValue());
        assertNull(callArgumentsParam.getData());
        assertNull(callArgumentsParam.getNonce());
        assertNull(callArgumentsParam.getChainId());
        assertNull(callArgumentsParam.getType());
        assertNull(callArgumentsParam.getRskSubtype());
    }

    @Test
    public void testToStringWithNoArgsParams() throws JsonProcessingException {
        String callArgumentsInput = "{ }";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        String result = callArgumentsParam.toString();

        String expected = "CallArguments{from='null', to='null', gas='null', gasLimit='null', gasPrice='null', value='null', data='null', nonce='null', chainId='null', type='null', rskSubtype='null'}";
        assertEquals(expected, result);
    }

    @Test
    public void testToStringIncludesFields() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"from\": \"" + FROM + "\"," +
                "            \"to\" : \"" + TO + "\"," +
                "            \"gas\": \"" + GAS + "\"," +
                "            \"gasPrice\":\"" + GAS_PRICE + "\"," +
                "            \"nonce\": \"" + NONCE + "\", " +
                "            \"chainId\": \"" + CHAIN_ID + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        String result = callArgumentsParam.toString();

        String expected = "CallArguments{from='7986b3df570230288501eea3d890bd66948c9b79', to='e7b8e91401bf4d1669f54dc5f98109d7efbc4eea', gas='0x76c0', gasLimit='null', gasPrice='0x9184e72a000', value='null', data='null', nonce='0x1', chainId='0x539', type='null', rskSubtype='null'}";
        assertEquals(expected, result);
    }

    @Test
    public void testTypeIsDeserialized() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"from\": \"" + FROM + "\"," +
                "            \"to\" : \"" + TO + "\"," +
                "            \"type\": \"0x1\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNotNull(callArgumentsParam.getType());
        assertEquals("0x1", callArgumentsParam.getType().getHexNumber());

        CallArguments callArguments = callArgumentsParam.toCallArguments();
        assertEquals("0x1", callArguments.getType());
    }

    @Test
    public void testRskSubtypeIsDeserialized() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"from\": \"" + FROM + "\"," +
                "            \"to\" : \"" + TO + "\"," +
                "            \"type\": \"0x2\"," +
                "            \"rskSubtype\": \"0x3\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNotNull(callArgumentsParam.getType());
        assertEquals("0x2", callArgumentsParam.getType().getHexNumber());
        assertNotNull(callArgumentsParam.getRskSubtype());
        assertEquals("0x3", callArgumentsParam.getRskSubtype().getHexNumber());

        CallArguments callArguments = callArgumentsParam.toCallArguments();
        assertEquals("0x2", callArguments.getType());
        assertEquals("0x3", callArguments.getRskSubtype());
    }

    @Test
    public void testTypeAndRskSubtypeAreNullWhenOmitted() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"from\": \"" + FROM + "\"}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNull(callArgumentsParam.getType());
        assertNull(callArgumentsParam.getRskSubtype());

        CallArguments callArguments = callArgumentsParam.toCallArguments();
        assertNull(callArguments.getType());
        assertNull(callArguments.getRskSubtype());
    }

    @Test
    public void testNullTypeAndRskSubtypeAreHandled() throws JsonProcessingException {
        String callArgumentsInput = "{\n" +
                "            \"type\": " + NULL + "," +
                "            \"rskSubtype\": " + NULL + "}";

        JsonNode jsonNode = objectMapper.readTree(callArgumentsInput);
        CallArgumentsParam callArgumentsParam = objectMapper.convertValue(jsonNode, CallArgumentsParam.class);

        assertNull(callArgumentsParam.getType());
        assertNull(callArgumentsParam.getRskSubtype());
    }
}
