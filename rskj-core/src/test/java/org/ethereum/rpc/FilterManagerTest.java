package org.ethereum.rpc;

import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

public class FilterManagerTest {

    @Test
    void whenGetFilterEvent_throwFilterNotFoundException() {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock);
        final var id = HexUtils.generateRandomUUIDToHexString();

        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.getFilterEvents(id, false),
                "filter not found"
        );
    }

    @Test
    void whenGetFilterEvent_throwFilterNotFoundExceptionBecauseTxFiltersExpired() throws InterruptedException {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock, 1, 1);
        final var id = filterManager.registerFilter(new PendingTransactionFilter());

        Transaction tx1 = Mockito.mock(Transaction.class);
        Mockito.when(tx1.getHash()).thenReturn(TestUtils.generateHash("txHash1"));

        Transaction tx2 = Mockito.mock(Transaction.class);
        Mockito.when(tx2.getHash()).thenReturn(TestUtils.generateHash("txHash2"));

        filterManager.newPendingTx(List.of(tx1));
        filterManager.newPendingTx(List.of(tx2));

        Thread.sleep(10);

        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.getFilterEvents(id, false),
                "filter not found"
        );
    }


    @Test
    void whenGetFilterEvent_throwFilterNotFoundExceptionBecauseBlockFiltersExpired() throws InterruptedException {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock, 1, 1);
        final var id = filterManager.registerFilter(new NewBlockFilter());

        Block block1 = Mockito.mock(Block.class);
        Mockito.when(block1.getHash()).thenReturn(TestUtils.generateHash("blockHash1"));

        Block block2 = Mockito.mock(Block.class);
        Mockito.when(block2.getHash()).thenReturn(TestUtils.generateHash("blockHash2"));

        filterManager.newBlockReceived(block1);
        filterManager.newBlockReceived(block2);

        Thread.sleep(10);

        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.getFilterEvents(id, false),
                "filter not found"
        );
    }
}
