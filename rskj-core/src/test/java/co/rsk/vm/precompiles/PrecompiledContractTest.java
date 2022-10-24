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

package co.rsk.vm.precompiles;

import co.rsk.config.TestSystemProperties;
import co.rsk.pcc.blockheader.BlockHeaderContract;
import co.rsk.peg.Bridge;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import co.rsk.pcc.bto.HDWalletUtils;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrecompiledContractTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);

    @Test
    void getBridgeContract() {
        DataWord bridgeAddress = DataWord.valueOf(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        PrecompiledContract bridge = precompiledContracts.getContractForAddress(null, bridgeAddress);

        Assertions.assertNotNull(bridge);
        Assertions.assertEquals(Bridge.class, bridge.getClass());
    }

    @Test
    void getBridgeContractTwice() {
        DataWord bridgeAddress = DataWord.valueOf(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        PrecompiledContract bridge1 = precompiledContracts.getContractForAddress(null, bridgeAddress);
        PrecompiledContract bridge2 = precompiledContracts.getContractForAddress(null, bridgeAddress);

        Assertions.assertNotNull(bridge1);
        Assertions.assertNotNull(bridge2);
        Assertions.assertNotSame(bridge1, bridge2);
    }

    @Test
    void getBlockHeaderContractBeforeRskip119() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP119)).thenReturn(false);
        DataWord blockHeaderContractAddress = DataWord.valueOf(PrecompiledContracts.BLOCK_HEADER_ADDR.getBytes());
        PrecompiledContract blockHeaderContract = precompiledContracts.getContractForAddress(activations, blockHeaderContractAddress);

        Assertions.assertNull(blockHeaderContract);
    }

    @Test
    void getBlockHeaderContractAfterRskip119() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP119)).thenReturn(true);
        DataWord blockHeaderContractAddress = DataWord.valueOf(PrecompiledContracts.BLOCK_HEADER_ADDR.getBytes());
        PrecompiledContract blockHeaderContract1 = precompiledContracts.getContractForAddress(activations, blockHeaderContractAddress);
        PrecompiledContract blockHeaderContract2 = precompiledContracts.getContractForAddress(activations, blockHeaderContractAddress);

        Assertions.assertNotNull(blockHeaderContract1);
        Assertions.assertNotNull(blockHeaderContract2);
        Assertions.assertEquals(BlockHeaderContract.class, blockHeaderContract1.getClass());
        Assertions.assertEquals(BlockHeaderContract.class, blockHeaderContract2.getClass());
        Assertions.assertNotSame(blockHeaderContract1, blockHeaderContract2);
    }

    @Test
    void getHdWalletUtilsBeforeRskip106() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP106)).thenReturn(false);
        DataWord btoUtilsAddress = DataWord.valueOf(PrecompiledContracts.HD_WALLET_UTILS_ADDR.getBytes());
        PrecompiledContract btoUtils = precompiledContracts.getContractForAddress(activations, btoUtilsAddress);

        Assertions.assertNull(btoUtils);
    }

    @Test
    void getHdWalletUtilsAfterRskip106() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP106)).thenReturn(true);
        DataWord btoUtilsAddress = DataWord.valueOf(PrecompiledContracts.HD_WALLET_UTILS_ADDR.getBytes());
        PrecompiledContract btoUtils1 = precompiledContracts.getContractForAddress(activations, btoUtilsAddress);
        PrecompiledContract btoUtils2 = precompiledContracts.getContractForAddress(activations, btoUtilsAddress);

        Assertions.assertNotNull(btoUtils1);
        Assertions.assertNotNull(btoUtils2);
        Assertions.assertEquals(HDWalletUtils.class, btoUtils1.getClass());
        Assertions.assertEquals(HDWalletUtils.class, btoUtils2.getClass());
        Assertions.assertNotSame(btoUtils1, btoUtils2);
    }
}
