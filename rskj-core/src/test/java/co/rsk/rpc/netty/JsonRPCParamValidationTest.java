/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package co.rsk.rpc.netty;

import co.rsk.rpc.Web3EthModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.AnnotationsErrorResolver;
import com.googlecode.jsonrpc4j.DefaultErrorResolver;
import com.googlecode.jsonrpc4j.JsonResponse;
import com.googlecode.jsonrpc4j.MultipleErrorResolver;
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.rpc.exception.RskErrorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonRPCParamValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonRpcCustomServer jsonRpcServer;
    private Web3EthModule handler;

    @BeforeEach
    void setUp() {
        handler = mock(Web3EthModule.class);
        this.jsonRpcServer = new JsonRpcCustomServer(handler, handler.getClass(), Collections.emptyList());
        jsonRpcServer.setErrorResolver(new MultipleErrorResolver(new RskErrorResolver(), AnnotationsErrorResolver.INSTANCE, DefaultErrorResolver.INSTANCE));
    }

    @Test
    void eth_getBlockByHash() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);

        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getBlockByHash\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0xad1328d13f833b8af722117afdc406a762033321df8e48c00cd372d462f48169\", \n" +
                "\t\ttrue\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getBlockByHash(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertNotNull(response);
        assertEquals(0, response.getCode());
    }

    @Test
    void eth_getBlockByHash_invalidHexCharInHash_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);

        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getBlockByHash\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0xc2b835zzz172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8\", \n" +
                "\t\ttrue\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getBlockByHash(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid block hash format. exception decoding Hex string: invalid characters encountered in Hex string", message);
    }

    @Test
    void eth_getBlockByHash_invalidHashLength_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getBlockByHash\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0xec576f474ea123c581c08008bea2\", \n" +
                "\t\ttrue\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getBlockByHash(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid block hash: incorrect length.", message);

    }

    @Test
    void eth_getTransactionByBlockHashAndIndex_invalidHash_returnsError() throws Exception {
        TransactionResultDTO resultDTO = mock(TransactionResultDTO.class);
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getTransactionByBlockHashAndIndex\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0x3c82bc62179602b6731037c49cba84e31ffe6e465a21c521a7\", \n" +
                "\t\t\"0x0\"\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);
        when(handler.eth_getTransactionByBlockHashAndIndex(any(), any())).thenReturn(resultDTO);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid block hash: incorrect length.", message);
    }


    @Test
    void eth_getTransactionByBlockHashAndIndex_invalidIndex_returnsError() throws Exception {
        TransactionResultDTO resultDTO = mock(TransactionResultDTO.class);
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getTransactionByBlockHashAndIndex\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0x3c82bc62179602b67318c013c10f99011037c49cba84e31ffe6e465a21c521a7\", \n" +
                "\t\t\"abc\"\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);
        when(handler.eth_getTransactionByBlockHashAndIndex(any(), any())).thenReturn(resultDTO);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid argument: abc: param should be a hex value string.", message);
    }

    @Test
    void eth_getBlockTransactionCountByHash_invalidHash_returnsError() throws Exception {
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getBlockTransactionCountByHash\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0x3c82bc62179602b6731037c49cba84e31ffe6e465a21c521a7\"\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);
        when(handler.eth_getBlockTransactionCountByHash(any())).thenReturn("0x0");
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid block hash: incorrect length.", message);
    }

    @Test
    void eth_getTransactionByHash_invalidHash_returnsError() throws Exception {
        TransactionResultDTO resultDTO = mock(TransactionResultDTO.class);
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getTransactionByHash\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0xc2b835zzz172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8\"\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid transaction hash format. exception decoding Hex string: invalid characters encountered in Hex string", message);
    }

    @Test
    void eth_getUncleByBlockHashAndIndex_invalidHash_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getUncleByBlockHashAndIndex\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0x3c82bc62179602b6731037c49cba84e31ffe6e465a21c521a7\", \n" +
                "\t\t\"0x0\"\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getUncleByBlockHashAndIndex(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid block hash: incorrect length.", message);
    }

    @Test
    void eth_getUncleByBlockHashAndIndex_invalidIndex_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);
        String requestBody = "{\n" +
                "\t\"jsonrpc\":\"2.0\",\n" +
                "\t\"method\":\"eth_getUncleByBlockHashAndIndex\",\n" +
                "\t\"params\":[\n" +
                "\t\t\"0x3c82bc62179602b67318c013c10f99011037c49cba84e31ffe6e465a21c521a7\", \n" +
                "\t\t\"122\"\n" +
                "\t],\n" +
                "\t\"id\":1\n" +
                "}";
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getUncleByBlockHashAndIndex(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid argument \"122\": param should be a hex value string.", message);
    }
}