package co.rsk.rpc.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonResponse;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.Web3Impl;
import org.ethereum.rpc.parameters.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Web3MethodResolutionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonRpcCustomServer jsonRpcServer;
    private Web3Impl web3Impl;

    @BeforeEach
    void setup() {
        web3Impl = mock(Web3Impl.class);
        this.jsonRpcServer = new JsonRpcCustomServer(web3Impl, Web3.class, Collections.emptyList(), objectMapper);
    }

    @Test
    void eth_call_withBlockRef_callsExpectedMethod() throws Exception {
        // Given
        String requestBody = """
                {
                  "jsonrpc": "2.0",
                  "method": "eth_call",
                  "params": [
                    {
                      "to": "0x1234567890123456789012345678901234567890",
                      "data": "0xabcdef01"
                    },
                    "latest"
                  ],
                  "id": 1
                }
                """;
        when(web3Impl.eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class))).thenReturn("ok");

        // When
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);

        // Then
        verify(web3Impl, times(1)).eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class));
        assertNotNull(response);
    }

    @Test
    void eth_call_byInputMap_isCallingRightMethod() throws Exception {
        // Given
        String requestBody = """
                {
                    "jsonrpc": "2.0",
                    "method": "eth_call",
                    "params": [
                        {
                            "to": "0x73ec81da0c72dd112e06c09a6ec03b5544d26f05",
                            "data": "0x46422b89"
                        },
                        {
                            "blockNumber": "0x1",
                            "requireCanonical": "true"
                        }
                    ],
                    "id": 1
                }
                """;
        when(web3Impl.eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class))).thenReturn("ok");

        // When
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);

        // Then
        verify(web3Impl, times(1)).eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class));
        assertNotNull(response);
    }

    @Test
    void eth_call_withOverridesAndBlockRef_callsExpectedMethod() throws Exception {
        // Given
        String requestBody = """
                {
                    "jsonrpc": "2.0",
                    "method": "eth_call",
                    "params": [
                        {
                            "to": "0x73ec81da0c72dd112e06c09a6ec03b5544d26f05",
                            "data": "0x46422b89"
                        },
                        {
                            "blockNumber": "0x1",
                            "requireCanonical": "false"
                        },
                        {
                            "0x77045e71a7a2c50903d88e564cd72fab11e82051": {
                                "code": "0x6103e760005260206000f3"
                            }
                        }
                    ],
                    "id": 1
                }
                """;
        when(web3Impl.eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class))).thenReturn("ok");

        // When
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);

        // Then
        verify(web3Impl, times(1)).eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class), anyMap());
        assertNotNull(response);
    }


    @Test
    void eth_call_withOverridesAndBlockNumber_callsExpectedMethod() throws Exception {
        // Given
        String requestBody = """
                {
                    "jsonrpc": "2.0",
                    "method": "eth_call",
                    "params": [
                        {
                            "to": "0x73ec81da0c72dd112e06c09a6ec03b5544d26f05",
                            "data": "0x46422b89"
                        },
                        "latest",
                        {
                            "0x77045e71a7a2c50903d88e564cd72fab11e82051": {
                                "code": "0x6103e760005260206000f3"
                            }
                        }
                    ],
                    "id": 1
                }
                """;
        when(web3Impl.eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class))).thenReturn("ok");

        // When
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);

        // Then
        verify(web3Impl, times(1)).eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class), anyMap());
        assertNotNull(response);
    }


    @Test
    void eth_call_withOverridesAndBlockRef_callsExpectedMethodAndParameters() throws Exception {
        // Given
        String fromAddress = "0x1234567890123456789012345678901234567bbb";
        String toAddress = "0x1234567890123456789012345678901234567890";
        String data = "0xabcdef01";
        String blockRef = "latest";
        String code = "0x6001600101";
        String balance = "0x0b";
        String nonce = "0x1";
        String storageKey = "0x01";
        String storageValue = "0x02";
        String addressOverride = "0xaaa4567890123456789012345678901234567890";

        String requestBody = String.format("""
                {
                  "jsonrpc": "2.0",
                  "method": "eth_call",
                  "params": [
                    {
                      "from": "%s",
                      "to": "%s",
                      "data": "%s"
                    },
                    "%s",
                    {
                      "%s": {
                        "code": "%s",
                        "balance": "%s",
                        "nonce": "%s",
                        "state": {
                          "%s": "%s"
                        }
                      }
                    }
                  ],
                  "id": 1
                }
                """, fromAddress, toAddress, data, blockRef, addressOverride, code, balance, nonce, storageKey, storageValue);

        when(web3Impl.eth_call(any(CallArgumentsParam.class), any(BlockRefParam.class), anyMap())).thenReturn("ok");

        // When
        JsonNode request = objectMapper.readTree(requestBody);
        JsonResponse response = jsonRpcServer.handleJsonNodeRequest(request);

        // Then
        ArgumentCaptor<CallArgumentsParam> argsCaptor = ArgumentCaptor.forClass(CallArgumentsParam.class);
        ArgumentCaptor<BlockRefParam> blockCaptor = ArgumentCaptor.forClass(BlockRefParam.class);
        ArgumentCaptor<Map<HexAddressParam, AccountOverrideParam>> overrideCaptor = ArgumentCaptor.forClass(Map.class);

        verify(web3Impl, times(1)).eth_call(argsCaptor.capture(), blockCaptor.capture(), overrideCaptor.capture());

        CallArgumentsParam args = argsCaptor.getValue();
        Map<HexAddressParam, AccountOverrideParam> overrides = overrideCaptor.getValue();

        assertEquals(toAddress, args.getTo().getAddress().toJsonString());
        assertEquals(data, args.getData().getAsHexString());

        assertNotNull(overrides);

        AccountOverrideParam override = overrides.entrySet().stream()
                .filter(entry -> entry.getKey().getAddress().toJsonString().equalsIgnoreCase(addressOverride))
                .map(Map.Entry::getValue)
                .findAny()
                .orElse(null);

        assertNotNull(override);
        assertEquals(code, override.code().getAsHexString());
        assertEquals(balance, override.balance().getHexNumber());
        assertEquals(nonce, override.nonce().getHexNumber());
        assertEquals(storageValue, override.state().get(new HexDataParam(storageKey)).getAsHexString());
        assertNotNull(response);
    }

}
