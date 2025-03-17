/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.util;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.config.blockchain.upgrades.NetworkUpgrade;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemUtilsTest {

    @Captor
    private ArgumentCaptor<String> formatCaptor;

    @Captor
    private ArgumentCaptor<Object> argCaptor;

    @Test
    void testPrintSystemInfo() {
        Logger logger = mock(Logger.class);

        SystemUtils.printSystemInfo(logger);

        verify(logger).info(formatCaptor.capture(), argCaptor.capture());

        Assertions.assertEquals("System info:\r  {}", formatCaptor.getValue());

        Object arg = argCaptor.getValue();
        Assertions.assertTrue(arg instanceof String);

        String stringArg = (String) arg;

        Map<String, String> params = Stream.of(stringArg.split("\r"))
                .map(s -> s.trim().split(": "))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        Assertions.assertTrue(params.containsKey("java.version"));
        Assertions.assertTrue(params.containsKey("java.runtime.name"));
        Assertions.assertTrue(params.containsKey("java.runtime.version"));
        Assertions.assertTrue(params.containsKey("java.vm.name"));
        Assertions.assertTrue(params.containsKey("java.vm.version"));
        Assertions.assertTrue(params.containsKey("java.vm.vendor"));
        Assertions.assertTrue(params.containsKey("os.name"));
        Assertions.assertTrue(params.containsKey("os.version"));
        Assertions.assertTrue(params.containsKey("os.arch"));
        Assertions.assertTrue(params.containsKey("processors"));
        Assertions.assertTrue(params.containsKey("memory.free"));
        Assertions.assertTrue(params.containsKey("memory.max"));
        Assertions.assertTrue(params.containsKey("memory.total"));
    }

    @Test
    void testPrintDisabledNetworkUpgrades() {
        final var logger = mock(Logger.class);
        final var blockchain = mock(Blockchain.class);
        final var bestBlock = mock(Block.class);
        final var bestBlockNumber = -1L;

        Mockito.when(blockchain.getBestBlock()).thenReturn(bestBlock);
        Mockito.when(bestBlock.getNumber()).thenReturn(bestBlockNumber);

        final var activationConfig = ActivationConfigsForTest.regtest();

        SystemUtils.printDisabledNetworkUpgrades(logger, blockchain, activationConfig);

        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.GENESIS.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.BAHAMAS.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.AFTER_BRIDGE_SYNC.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.ORCHID.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.ORCHID_060.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.WASABI_100.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.PAPYRUS_200.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.TWOTOTHREE.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.IRIS300.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.HOP400.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.HOP401.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.FINGERROOT500.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.ARROWHEAD600.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.ARROWHEAD631.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.LOVELL700.name()),Mockito.eq(bestBlockNumber));
    }

    @Test
    void testPrintDisabledNetworkUpgradesWithNoDisabledNetworkUpgrade() {
        final var logger = mock(Logger.class);
        final var blockchain = mock(Blockchain.class);
        final var bestBlock = mock(Block.class);
        final var bestBlockNumber = 10L;

        Mockito.when(blockchain.getBestBlock()).thenReturn(bestBlock);
        Mockito.when(bestBlock.getNumber()).thenReturn(bestBlockNumber);

        final var activationConfig = ActivationConfigsForTest.regtest();

        SystemUtils.printDisabledNetworkUpgrades(logger, blockchain, activationConfig);

        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.GENESIS.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.AFTER_BRIDGE_SYNC.name()),Mockito.eq(bestBlockNumber));
        verify(logger, times(1)).warn(Mockito.eq("WARNING: Network upgrade {} is DISABLED. Best block number is: {}."), Mockito.eq(NetworkUpgrade.ARROWHEAD631.name()),Mockito.eq(bestBlockNumber));
    }
}
