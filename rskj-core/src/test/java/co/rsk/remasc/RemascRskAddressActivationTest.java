/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.remasc;

import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

public class RemascRskAddressActivationTest {

    @Test
    public void testActivation() {
        final RskAddress rskLabsAddress = new RskAddress("14d3065c8Eb89895f4df12450EC6b130049F8034");
        final RskAddress rskLabsAddressRskip218 = new RskAddress("dcb12179ba4697350f66224c959bdd9c282818df");

        final RemascTransaction txMock = mock(RemascTransaction.class);
        final Repository repositoryMock = mock(Repository.class);
        final BlockStore blockStoreMock = mock(BlockStore.class);
        final List<LogInfo> logs = Collections.emptyList();
        final ActivationConfig activationConfig = mock(ActivationConfig.class);
        final Block blockMock = mock(Block.class);
        final RemascConfig remascConfig = spy(new RemascConfigFactory(RemascContract.REMASC_CONFIG)
                .createRemascConfig("regtest"));

        final Remasc remasc = new Remasc(Constants.regtest(), activationConfig, repositoryMock,
                blockStoreMock, remascConfig, txMock, PrecompiledContracts.REMASC_ADDR, blockMock, logs);

        when(remascConfig.getRskLabsAddress()).thenReturn(rskLabsAddress);
        when(remascConfig.getRskLabsAddressRskip218()).thenReturn(rskLabsAddressRskip218);

        when(activationConfig.isActive(ConsensusRule.RSKIP218, 1)).thenReturn(false);
        when(activationConfig.isActive(ConsensusRule.RSKIP218, 2)).thenReturn(true);

        when(blockMock.getNumber()).thenReturn(1L);

        RskAddress actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(rskLabsAddress, actualAddress);
        Assert.assertEquals(blockMock.getNumber(), 1L);
        Assert.assertFalse(activationConfig.isActive(ConsensusRule.RSKIP218, blockMock.getNumber()));
        verify(remascConfig).getRskLabsAddress();

        when(blockMock.getNumber()).thenReturn(2L);

        actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(rskLabsAddressRskip218, actualAddress);
        Assert.assertEquals(blockMock.getNumber(), 2L);
        Assert.assertTrue(activationConfig.isActive(ConsensusRule.RSKIP218, blockMock.getNumber()));
        verify(remascConfig).getRskLabsAddressRskip218();
    }
}
