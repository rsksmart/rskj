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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.spy;

public class RemascRskAddressActivationTest {

    private RemascConfig remascConfig;
    private Block blockMock;
    private Remasc remasc;

    @Before
    public void setUp() {
        RemascTransaction txMock = Mockito.mock(RemascTransaction.class);
        Repository repositoryMock = Mockito.mock(Repository.class);
        BlockStore blockStoreMock = Mockito.mock(BlockStore.class);
        ActivationConfig activationConfig = new TestSystemProperties().getActivationConfig();
        List<LogInfo> logs = Collections.emptyList();

        blockMock = mock(Block.class);
        remascConfig = spy(new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("regtest"));
        remasc = new Remasc(Constants.regtest(), activationConfig, repositoryMock, blockStoreMock, remascConfig, txMock, PrecompiledContracts.REMASC_ADDR, blockMock, logs);
    }

    @Test
    public void testActivation() {
        final RskAddress oldAddress = new RskAddress("0000000000000000000000000000000001000001");
        final RskAddress newAddress = new RskAddress("0000000000000000000000000000000001000002");

        when(remascConfig.getRskLabsAddress()).thenReturn(oldAddress);
        when(remascConfig.getRskLabsAddressRskipXyz()).thenReturn(newAddress);

        when(blockMock.getNumber()).thenReturn(4L);
        RskAddress actualAddress = remasc.getRskLabsAddress();
        Assert.assertEquals(oldAddress, actualAddress);
        verify(remascConfig).getRskLabsAddress();

        when(blockMock.getNumber()).thenReturn(5L);
        actualAddress = remasc.getRskLabsAddress();
        Assert.assertEquals(newAddress, actualAddress);
        verify(remascConfig).getRskLabsAddressRskipXyz();
    }
}
