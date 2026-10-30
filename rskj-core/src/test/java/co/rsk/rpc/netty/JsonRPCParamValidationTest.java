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

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.net.TransactionGateway;
import co.rsk.rpc.Web3EthModule;
import co.rsk.rpc.modules.eth.EthModuleTransactionBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.AnnotationsErrorResolver;
import com.googlecode.jsonrpc4j.DefaultErrorResolver;
import com.googlecode.jsonrpc4j.JsonResponse;
import com.googlecode.jsonrpc4j.MultipleErrorResolver;
import org.ethereum.config.Constants;
import org.ethereum.core.TransactionPool;
import org.ethereum.datasource.HashMapDB;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonRPCParamValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonRpcCustomServer jsonRpcServer;
    private Web3EthModule handler;

    @BeforeEach
    void setUp() {
        handler = mock(Web3EthModule.class);
        this.jsonRpcServer = new JsonRpcCustomServer(handler, handler.getClass(), Collections.emptyList(), objectMapper);
        jsonRpcServer.setErrorResolver(new MultipleErrorResolver(new RskErrorResolver(), AnnotationsErrorResolver.INSTANCE, DefaultErrorResolver.INSTANCE));
    }

    @Test
    void eth_sendTransaction_accessListAddressNotHex_returnsInvalidParams() throws Exception {
        JsonResponse response = sendTransaction("""
                "type": "0x1",
                "accessList": [{"address": "0xzz%s", "storageKeys": []}]
                """.formatted("00".repeat(19)));

        assertEquals(-32602, response.getCode());
        assertEquals("Access list entry address must be a 20-byte hex value at index 0",
                response.getResponse().get("error").get("message").asText());
    }

    @Test
    void eth_sendTransaction_authorizationListRNotHex_returnsInvalidParams() throws Exception {
        JsonResponse response = sendTransaction("""
                "type": "0x4",
                "authorizationList": [{
                    "chainId": "0x21",
                    "address": "0x%s",
                    "nonce": "0x0",
                    "yParity": "0x0",
                    "r": "0xzz",
                    "s": "0x1"
                }]
                """.formatted("11".repeat(20)));

        assertEquals(-32602, response.getCode());
        assertEquals("Authorization list entry r must be hex at index 0",
                response.getResponse().get("error").get("message").asText());
    }

    /** Sends a raw eth_sendTransaction request from a wallet account, with {@code typedFields} added to its arguments. */
    private JsonResponse sendTransaction(String typedFields) throws Exception {
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();
        TransactionGateway transactionGateway = mock(TransactionGateway.class);
        EthModuleTransactionBase ethModuleTransaction = new EthModuleTransactionBase(
                Constants.regtest(), wallet, mock(TransactionPool.class), transactionGateway);
        when(handler.eth_sendTransaction(any())).thenAnswer(
                invocation -> ethModuleTransaction.sendTransaction(invocation.getArgument(0)));

        String requestBody = """
                {
                    "jsonrpc": "2.0",
                    "method": "eth_sendTransaction",
                    "params": [{
                        "from": "%s",
                        "to": "%s",
                        "gas": "0x76c0",
                        "gasPrice": "0x9184e72a000",
                        "value": "0x186a0",
                        "nonce": "0x1",
                        "chainId": "0x21",
                        %s
                    }],
                    "id": 1
                }
                """.formatted(sender.toJsonString(), receiver.toJsonString(), typedFields);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(objectMapper.readTree(requestBody));
        verify(transactionGateway, never()).receiveTransaction(any());
        return response;
    }

    @Test
    void eth_getBlockByHash() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);

        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getBlockByHash",
                    "params":[
                        "0xad1328d13f833b8af722117afdc406a762033321df8e48c00cd372d462f48169",
                        true
                    ],
                    "id":1
                }
                """;
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getBlockByHash(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertNotNull(response);
        assertEquals(0, response.getCode());
    }

    @Test
    void eth_getBlockByHash_invalidHexCharInHash_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);

        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getBlockByHash",
                    "params":[
                        "0xc2b835zzz172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8",
                        true
                    ],
                    "id":1
                }
                """;
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getBlockByHash(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid block hash format: invalid hex value", message);
    }

    @Test
    void eth_getBlockByHash_invalidHashLength_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getBlockByHash",
                    "params":[
                        "0xec576f474ea123c581c08008bea2",
                        true
                    ],
                    "id":1
                }
                """;
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
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getTransactionByBlockHashAndIndex",
                    "params":[
                        "0x3c82bc62179602b6731037c49cba84e31ffe6e465a21c521a7",
                        "0x0"
                    ],
                    "id":1
                }
                """;
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
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getTransactionByBlockHashAndIndex",
                    "params":[
                        "0x3c82bc62179602b67318c013c10f99011037c49cba84e31ffe6e465a21c521a7",
                        "abc"
                    ],
                    "id":1
                }
                """;
        JsonNode request = objectMapper.readTree(requestBody);
        when(handler.eth_getTransactionByBlockHashAndIndex(any(), any())).thenReturn(resultDTO);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid argument \"abc\": param should be a hex value string.", message);
    }

    @Test
    void eth_getBlockTransactionCountByHash_invalidHash_returnsError() throws Exception {
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getBlockTransactionCountByHash",
                    "params":[
                        "0x3c82bc62179602b6731037c49cba84e31ffe6e465a21c521a7"
                    ],
                    "id":1
                }
                """;
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
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getTransactionByHash",
                    "params":[
                        "0xc2b835zzz172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8"
                    ],
                    "id":1
                }
                """;
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid transaction hash format: invalid hex value", message);
    }

    @Test
    void eth_getUncleByBlockHashAndIndex_invalidHash_returnsError() throws Exception {
        BlockResultDTO blockResultDTO = mock(BlockResultDTO.class);
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getUncleByBlockHashAndIndex",
                    "params":[
                        "0x3c82bc62179602b6731037c49cba84e31ffe6e465a21c521a7",
                        "0x0"
                    ],
                    "id":1
                }
                """;
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
        String requestBody = """
                {
                    "jsonrpc":"2.0",
                    "method":"eth_getUncleByBlockHashAndIndex",
                    "params":[
                        "0x3c82bc62179602b67318c013c10f99011037c49cba84e31ffe6e465a21c521a7",
                        "122"
                    ],
                    "id":1
                }
                """;
        JsonNode request = objectMapper.readTree(requestBody);

        when(handler.eth_getUncleByBlockHashAndIndex(any(), any())).thenReturn(blockResultDTO);

        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);
        assertEquals(-32602, response.getCode());
        String message = response.getResponse().get("error").get("message").asText();
        assertEquals("Invalid argument \"122\": param should be a hex value string.", message);
    }
}
