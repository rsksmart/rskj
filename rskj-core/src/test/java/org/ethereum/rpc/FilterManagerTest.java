package org.ethereum.rpc;

import co.rsk.util.HexUtils;
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
    void whenGetFilterEvent_throwFilterNotFoundExceptionBecauseTxFiltersExpired() {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock, 1, 1);
        final var id = filterManager.registerFilter(new PendingTransactionFilter());

        filterManager.newPendingTx(List.of(Mockito.mock(Transaction.class)));
        filterManager.newPendingTx(List.of(Mockito.mock(Transaction.class)));

        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.getFilterEvents(id, false),
                "filter not found"
        );
    }


    @Test
    void whenGetFilterEvent_throwFilterNotFoundExceptionBecauseBlockFiltersExpired() {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock, 1, 1);
        final var id = filterManager.registerFilter(new NewBlockFilter());

        filterManager.newBlockReceived(Mockito.mock(Block.class));
        filterManager.newBlockReceived(Mockito.mock(Block.class));

        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.getFilterEvents(id, false),
                "filter not found"
        );
    }
}
