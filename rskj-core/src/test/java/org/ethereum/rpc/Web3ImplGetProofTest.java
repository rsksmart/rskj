/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package org.ethereum.rpc;

import co.rsk.core.RskAddress;
import co.rsk.rpc.modules.eth.EthModule;
import org.ethereum.TestUtils;
import org.ethereum.rpc.dto.ProofResultDTO;
import org.ethereum.rpc.dto.StorageProofDTO;
import org.ethereum.rpc.parameters.BlockRefParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataArrayParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for eth_getProof and rsk_getProof RPC endpoints.
 */
class Web3ImplGetProofTest {

    private Web3Impl web3;
    private EthModule ethModule;
    private RskAddress testAddress;

    @BeforeEach
    void setUp() {
        ethModule = mock(EthModule.class);
        testAddress = TestUtils.generateAddress("testAccount");

        // Build web3 with mocked ethModule
        web3 = Web3TestBuilder.builder()
                .withEthModule(ethModule)
                .build();
    }

    @Test
    void testGetProofForEOA() throws Exception {
        // Setup mock response
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[]{"0x1234"})
                .balance("0x100")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), anyString(), eq(true)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        assertEquals(testAddress.toJsonString(), result.getAddress());
        assertEquals("0x100", result.getBalance());
        assertEquals("0x0", result.getNonce());
        assertEquals(0, result.getStorageProof().length);
    }

    @Test
    void testGetProofWithStorageKeys() throws Exception {
        // Setup storage proof
        StorageProofDTO storageProof = new StorageProofDTO(
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x42",
                new String[]{"0xabcd"}
        );

        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[]{"0x1234"})
                .balance("0x100")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[]{storageProof})
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), anyString(), eq(true)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        List<String> keys = List.of("0x0000000000000000000000000000000000000000000000000000000000000000");
        HexDataArrayParam storageKeys = new HexDataArrayParam(keys);
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        assertEquals(1, result.getStorageProof().length);
        assertEquals("0x42", result.getStorageProof()[0].getValue());
    }

    @Test
    void testGetProofAtSpecificBlockNumber() throws Exception {
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[0])
                .balance("0x0")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), eq("0x0"), eq(true)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("0x0");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        verify(ethModule).getProof(any(), any(), eq("0x0"), eq(true));
    }

    @Test
    void testGetProofEarliestBlock() throws Exception {
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[0])
                .balance("0x0")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), eq("earliest"), eq(true)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("earliest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        verify(ethModule).getProof(any(), any(), eq("earliest"), eq(true));
    }

    @Test
    void testGetProofLatestBlock() throws Exception {
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[0])
                .balance("0x0")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), eq("latest"), eq(true)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        verify(ethModule).getProof(any(), any(), eq("latest"), eq(true));
    }

    @Test
    void testRskGetProofUsesNativeFormat() throws Exception {
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[]{"0x5678"})
                .balance("0x100")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        // rsk_getProof should use useRlpEncoding=false
        when(ethModule.getProof(any(RskAddress.class), anyList(), anyString(), eq(false)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.rsk_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        // Verify rsk_getProof was called with useRlpEncoding=false
        verify(ethModule).getProof(any(), any(), eq("latest"), eq(false));
    }

    @Test
    void testEthGetProofUsesRlpEncoding() throws Exception {
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[]{"0x1234"})
                .balance("0x100")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        // eth_getProof should use useRlpEncoding=true
        when(ethModule.getProof(any(RskAddress.class), anyList(), anyString(), eq(true)))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        // Verify eth_getProof was called with useRlpEncoding=true
        verify(ethModule).getProof(any(), any(), eq("latest"), eq(true));
    }

    @Test
    void testGetProofWithMultipleStorageKeys() throws Exception {
        StorageProofDTO[] proofs = new StorageProofDTO[]{
                new StorageProofDTO("0x0000000000000000000000000000000000000000000000000000000000000000", "0x0", new String[]{}),
                new StorageProofDTO("0x0000000000000000000000000000000000000000000000000000000000000001", "0x0", new String[]{}),
                new StorageProofDTO("0x0000000000000000000000000000000000000000000000000000000000000002", "0x0", new String[]{})
        };

        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[0])
                .balance("0x0")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x0")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(proofs)
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        List<String> keys = List.of(
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x0000000000000000000000000000000000000000000000000000000000000002"
        );
        HexDataArrayParam storageKeys = new HexDataArrayParam(keys);
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        assertNotNull(result);
        assertEquals(3, result.getStorageProof().length);
    }

    @Test
    void testProofResultDTOStructure() throws Exception {
        ProofResultDTO mockResult = ProofResultDTO.builder()
                .address(testAddress.toJsonString())
                .accountProof(new String[]{"0x1234"})
                .balance("0x100")
                .codeHash("0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
                .nonce("0x1")
                .storageHash("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421")
                .storageProof(new StorageProofDTO[0])
                .build();

        when(ethModule.getProof(any(RskAddress.class), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResult);

        HexAddressParam addressParam = new HexAddressParam(testAddress.toHexString());
        HexDataArrayParam storageKeys = new HexDataArrayParam(Collections.emptyList());
        BlockRefParam blockRef = new BlockRefParam("latest");

        ProofResultDTO result = web3.eth_getProof(addressParam, storageKeys, blockRef);

        // Verify all required fields are present and properly formatted
        assertNotNull(result.getAddress(), "address should not be null");
        assertTrue(result.getAddress().startsWith("0x"), "address should be hex prefixed");

        assertNotNull(result.getAccountProof(), "accountProof should not be null");

        assertNotNull(result.getBalance(), "balance should not be null");
        assertTrue(result.getBalance().startsWith("0x"), "balance should be hex prefixed");

        assertNotNull(result.getCodeHash(), "codeHash should not be null");
        assertTrue(result.getCodeHash().startsWith("0x"), "codeHash should be hex prefixed");

        assertNotNull(result.getNonce(), "nonce should not be null");
        assertTrue(result.getNonce().startsWith("0x"), "nonce should be hex prefixed");

        assertNotNull(result.getStorageHash(), "storageHash should not be null");
        assertTrue(result.getStorageHash().startsWith("0x"), "storageHash should be hex prefixed");

        assertNotNull(result.getStorageProof(), "storageProof should not be null");
    }
}
