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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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
import static org.powermock.api.mockito.PowerMockito.spy;

public class RemascRskAddressActivationTest {

    @Test
    public void testRskip218Activation() {
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

    @Test
    public void testRemascAddressChangesOnEachActivation_addressShouldBeAsDefinedByEachActivation() {
        final RskAddress rskLabsAddress = new RskAddress("14d3065c8Eb89895f4df12450EC6b130049F8034");
        final RskAddress rskLabsAddressRskip218 = new RskAddress("dcb12179ba4697350f66224c959bdd9c282818df");
        final RskAddress rskLabsAddressRskip348 = new RskAddress("dcb12179ba4697350f66224c959bdd9c282818df"); // TODO -> Update this when ready

        final RemascTransaction txMock = mock(RemascTransaction.class);
        final Repository repositoryMock = mock(Repository.class);
        final BlockStore blockStoreMock = mock(BlockStore.class);
        final List<LogInfo> logs = Collections.emptyList();
        final Block blockMock = mock(Block.class);
        final RemascConfig remascConfig = spy(new RemascConfigFactory(RemascContract.REMASC_CONFIG)
                .createRemascConfig("regtest"));

        final ActivationConfig activationConfig = ActivationConfig.read(testConfig);

        final Remasc remasc = new Remasc(Constants.regtest(), activationConfig, repositoryMock,
                blockStoreMock, remascConfig, txMock, PrecompiledContracts.REMASC_ADDR, blockMock, logs);

        // Remasc Config Mocks
        when(remascConfig.getRskLabsAddress()).thenReturn(rskLabsAddress);
        when(remascConfig.getRskLabsAddressRskip218()).thenReturn(rskLabsAddressRskip218);
        when(remascConfig.getRskLabsAddressRskip348()).thenReturn(rskLabsAddressRskip348);

        // RSK IP #218 not yet activated
        long blockHeight = 1L;

        when(blockMock.getNumber()).thenReturn(blockHeight);

        RskAddress actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(rskLabsAddress, actualAddress);
        Assert.assertFalse(activationConfig.isActive(ConsensusRule.RSKIP218, blockHeight));
        verify(remascConfig, times(1)).getRskLabsAddress();

        // RSK IP #218 just activated
        blockHeight = 8L;
        when(blockMock.getNumber()).thenReturn(blockHeight);

        actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(rskLabsAddressRskip218, actualAddress);
        Assert.assertTrue(activationConfig.isActive(ConsensusRule.RSKIP218, blockHeight));
        verify(remascConfig, times(1)).getRskLabsAddressRskip218();

        // RSK IP #348 just activated
        blockHeight = 10L;
        when(blockMock.getNumber()).thenReturn(blockHeight);

        actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(rskLabsAddressRskip348, actualAddress);
        Assert.assertTrue(activationConfig.isActive(ConsensusRule.RSKIP348, blockHeight));
        verify(remascConfig, times(1)).getRskLabsAddressRskip348();
    }

    private final Config testConfig = ConfigFactory.parseString(String.join("\n",
            "hardforkActivationHeights: {",
            "    genesis: 0",
            "    bahamas: 1",
            "    afterBridgeSync: 2,",
            "    orchid: 3,",
            "    orchid060: 4,",
            "    wasabi100: 5",
            "    papyrus200: 6",
            "    twoToThree: 7",
            "    iris300: 8",
            "    hop400: 9",
            "    finger500: 10",
            "},",
            "consensusRules: {",
            "    areBridgeTxsPaid: afterBridgeSync,",
            "    rskip85: orchid,",
            "    rskip87: orchid,",
            "    rskip88: orchid,",
            "    rskip89: orchid,",
            "    rskip90: orchid,",
            "    rskip91: orchid,",
            "    rskip92: orchid,",
            "    rskip97: orchid,",
            "    rskip98: orchid,",
            "    rskip103: orchid060,",
            "    rskip106: wasabi100,",
            "    rskip110: wasabi100,",
            "    rskip119: wasabi100,",
            "    rskip120: wasabi100,",
            "    rskip122: wasabi100,",
            "    rskip123: wasabi100,",
            "    rskip124: wasabi100,",
            "    rskip125: wasabi100",
            "    rskip126: wasabi100",
            "    rskip132: wasabi100",
            "    rskip134: papyrus200",
            "    rskip136: bahamas",
            "    rskip137: papyrus200",
            "    rskip140: papyrus200,",
            "    rskip143: papyrus200",
            "    rskip146: papyrus200",
            "    rskip150: twoToThree",
            "    rskip151: papyrus200",
            "    rskip152: papyrus200",
            "    rskip156: papyrus200",
            "    rskipUMM: papyrus200",
            "    rskip153: iris300",
            "    rskip169: iris300",
            "    rskip170: iris300",
            "    rskip171: iris300",
            "    rskip174: iris300",
            "    rskip176: iris300",
            "    rskip179: iris300",
            "    rskip180: iris300",
            "    rskip181: iris300",
            "    rskip185: iris300",
            "    rskip186: iris300",
            "    rskip191: iris300",
            "    rskip197: iris300",
            "    rskip199: iris300",
            "    rskip200: iris300",
            "    rskip201: iris300",
            "    rskip218: iris300",
            "    rskip219: iris300",
            "    rskip220: iris300",
            "    rskip271: hop400",
            "    rskip284: hop400",
            "    rskip290: hop400",
            "    rskip293: hop400",
            "    rskip294: hop400",
            "    rskip297: hop400",
            "    rskip348: finger500",
            "}"
    ));

}
