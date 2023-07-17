package org.ethereum.rpc;

import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FilterManagerTest {

    @Test
    void whenGetFilterEvent_throwFilterNotFoundException() {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock);

        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.getFilterEvents(1, false),
                "filter not found"
        );
    }

    @Test
    void whenRemoveFilter_throwFilterNotFoundException() {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        FilterManager filterManager = new FilterManager(ethMock);

        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> filterManager.removeFilter(1));
        Assertions.assertThrowsExactly(
                RskJsonRpcRequestException.class,
                () -> filterManager.removeFilter(1),
                "filter not found"
        );
    }
}
