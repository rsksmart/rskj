/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.db;

import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.datasource.*;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 3/1/2016.
 */
class ReceiptStoreImplTest {

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void flushDataSource(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        store.flush();

        verify(baseDataSource, times(1)).flush();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void closeDataSource(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        store.close();

        verify(baseDataSource, times(1)).close();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void getUnknownKey(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        byte[] key = new byte[]{0x01, 0x02};

        Optional<TransactionInfo> result = store.get(key, key);

        Assertions.assertFalse(result.isPresent());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addAndGetTransaction(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash).orElse(null);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getBlockHash());
        Assertions.assertArrayEquals(blockHash, result.getBlockHash());
        Assertions.assertEquals(42, result.getIndex());
        Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addAndGetTransactionWith128AsIndex(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 128, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash).orElse(null);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getBlockHash());
        Assertions.assertArrayEquals(blockHash, result.getBlockHash());
        Assertions.assertEquals(128, result.getIndex());
        Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addAndGetTransactionWith238AsIndex(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 238, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash).orElse(null);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getBlockHash());
        Assertions.assertArrayEquals(blockHash, result.getBlockHash());
        Assertions.assertEquals(238, result.getIndex());
        Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addTwoTransactionsAndGetLastTransaction(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("010203040506070809");

        store.add(blockHash0, 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash).orElse(null);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getBlockHash());
        Assertions.assertArrayEquals(blockHash, result.getBlockHash());
        Assertions.assertEquals(42, result.getIndex());
        Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addTwoTransactionsAndGetAllTransactions(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("010203040506070809");

        store.add(blockHash0, 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 42, receipt);

        Optional<TransactionInfo> txInfo0 = store.get(receipt.getTransaction().getHash().getBytes(), blockHash0);

        Assertions.assertTrue(txInfo0.isPresent());

        Assertions.assertNotNull(txInfo0.get().getBlockHash());
        Assertions.assertArrayEquals(blockHash0, txInfo0.get().getBlockHash());
        Assertions.assertEquals(3, txInfo0.get().getIndex());
        Assertions.assertArrayEquals(receipt0.getEncoded(), txInfo0.get().getReceipt().getEncoded());

        Optional<TransactionInfo> txInfo = store.get(receipt.getTransaction().getHash().getBytes(), blockHash);

        Assertions.assertTrue(txInfo.isPresent());

        Assertions.assertNotNull(txInfo.get().getBlockHash());
        Assertions.assertArrayEquals(blockHash, txInfo.get().getBlockHash());
        Assertions.assertEquals(42, txInfo.get().getIndex());
        Assertions.assertArrayEquals(receipt.getEncoded(), txInfo.get().getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void getUnknownTransactionByBlock(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt = createReceipt();

        Keccak256 blockHash = TestUtils.randomHash();

        Optional<TransactionInfo> resultOpt = store.get(receipt.getTransaction().getHash().getBytes(), blockHash.getBytes());

        Assertions.assertFalse(resultOpt.isPresent());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void getTransactionByUnknownBlock(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt = createReceipt();

        Keccak256 blockHash0 = new Keccak256("0102030405060708000000000000000000000000000000000000000000000000");
        Keccak256 blockHash = new Keccak256("0102030405060708090000000000000000000000000000000000000000000000");

        store.add(blockHash.getBytes(), 1, receipt);

        Optional<TransactionInfo> resultOpt = store.get(receipt.getTransaction().getHash().getBytes(), blockHash0.getBytes());

        Assertions.assertFalse(resultOpt.isPresent());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addTwoTransactionsAndGetTransactionByFirstBlock(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt0 = createReceipt();
        Keccak256 blockHash0 = new Keccak256("0102030405060708090000000000000000000000000000000000000000000000");

        store.add(blockHash0.getBytes(), 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        Keccak256 blockHash = new Keccak256("0102030405060708000000000000000000000000000000000000000000000000");

        store.add(blockHash.getBytes(), 42, receipt);

        Optional<TransactionInfo> resultOpt = store.get(receipt.getTransaction().getHash().getBytes(), blockHash0.getBytes());
        TransactionInfo result = resultOpt.get();

        Assertions.assertNotNull(result.getBlockHash());
        Assertions.assertArrayEquals(blockHash0.getBytes(), result.getBlockHash());
        Assertions.assertEquals(3, result.getIndex());
        Assertions.assertArrayEquals(receipt0.getEncoded(), result.getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addTwoTransactionsAndGetTransactionBySecondBlock(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        TransactionReceipt receipt0 = createReceipt();
        Keccak256 blockHash0 = new Keccak256("0102030405060708090000000000000000000000000000000000000000000000");

        store.add(blockHash0.getBytes(), 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        Keccak256 blockHash = new Keccak256("0102030405060708000000000000000000000000000000000000000000000000");

        store.add(blockHash.getBytes(), 42, receipt);

        Optional<TransactionInfo> resultOpt = store.get(receipt.getTransaction().getHash().getBytes(), blockHash.getBytes());
        TransactionInfo result = resultOpt.get();

        Assertions.assertNotNull(result.getBlockHash());
        Assertions.assertArrayEquals(blockHash.getBytes(), result.getBlockHash());
        Assertions.assertEquals(42, result.getIndex());
        Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ArgumentsSource(ReceiptStoreArgumentsProvider.class)
    void addFourTransactionsAndGetReceiptByBlockHashAndBestInChain(String version, KeyValueDataSource baseDataSource, ReceiptStore store) {
        for (int i = 0; i < 4; i++) {
            TransactionReceipt receipt = createReceipt(i);
            Keccak256 blockHash = new Keccak256("01020304050607080900000000000000000000000000000000000000000000" + String.format("%02d" , i));

            store.add(blockHash.getBytes(), i, receipt);
        }

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(1L);
        BlockStore blockStore = mock(BlockStore.class);

        for (int i = 0; i < 4; i++) {
            TransactionReceipt receipt = createReceipt(i);
            Keccak256 blockHash = new Keccak256("01020304050607080900000000000000000000000000000000000000000000" + String.format("%02d" , i));

            TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash.getBytes()).orElse(null);

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getBlockHash());
            Assertions.assertArrayEquals(blockHash.getBytes(), result.getBlockHash());
            Assertions.assertEquals(i, result.getIndex());
            Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());

            when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
            when(blockStore.getChainBlockByNumber(1)).thenReturn(block);
            when(block.getHash()).thenReturn(blockHash);

            result = store.getInMainChain(receipt.getTransaction().getHash().getBytes(), blockStore).orElse(null);

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getBlockHash());
            Assertions.assertArrayEquals(blockHash.getBytes(), result.getBlockHash());
            Assertions.assertEquals(i, result.getIndex());
            Assertions.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
        }
    }

    private static TransactionReceipt createReceipt() {
        return createReceipt(1);
    }

    // from TransactionTest
    private static TransactionReceipt createReceipt(int numOfLogs) {
        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        List<LogInfo> logs = new ArrayList<>();

        for (int i = 0; i < numOfLogs; i++) {
            LogInfo logInfo = new LogInfo(
                    Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                    null,
                    Hex.decode("a1a1a1")
            );
            logs.add(logInfo);
        }

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, new byte[]{0x01});
        receipt.setTransaction(Transaction.builder().build());
        return receipt;
    }

    private static class ReceiptStoreArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            KeyValueDataSource baseDataSourceV1 = spy(new HashMapDB());
            KeyValueDataSource baseDataSourceV2 = spy(new HashMapDB());
            return Stream.of(
                    Arguments.of("V1", baseDataSourceV1, new ReceiptStoreImpl(baseDataSourceV1)),
                    Arguments.of("V2", baseDataSourceV2, new ReceiptStoreImplV2(baseDataSourceV2))
            );
        }
    }
}
