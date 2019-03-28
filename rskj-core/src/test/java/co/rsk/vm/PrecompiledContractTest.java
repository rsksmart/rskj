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

package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.pcc.blockheader.BlockHeaderContract;
import co.rsk.peg.Bridge;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrecompiledContractTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);

    @Test
    public void getBridgeContract() {
        DataWord bridgeAddress = DataWord.valueOf(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        PrecompiledContract bridge = precompiledContracts.getContractForAddress(null, bridgeAddress);

        Assert.assertNotNull(bridge);
        Assert.assertEquals(Bridge.class, bridge.getClass());
    }

    @Test
    public void getBridgeContractTwice() {
        DataWord bridgeAddress = DataWord.valueOf(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        PrecompiledContract bridge1 = precompiledContracts.getContractForAddress(null, bridgeAddress);
        PrecompiledContract bridge2 = precompiledContracts.getContractForAddress(null, bridgeAddress);

        Assert.assertNotNull(bridge1);
        Assert.assertNotNull(bridge2);
        Assert.assertNotSame(bridge1, bridge2);
    }

    @Test
    public void getBlockHeaderContractBeforeRskip119() {
        BlockchainConfig afterRskip119 = mock(BlockchainConfig.class);
        when(afterRskip119.isRskip119()).thenReturn(false);
        DataWord blockHeaderContractAddress = DataWord.valueOf(PrecompiledContracts.BLOCK_HEADER_ADDR.getBytes());
        PrecompiledContract blockHeaderContract = precompiledContracts.getContractForAddress(afterRskip119, blockHeaderContractAddress);

        Assert.assertNull(blockHeaderContract);
    }

    @Test
    public void getBlockHeaderContractAfterRskip119() {
        BlockchainConfig afterRskip119 = mock(BlockchainConfig.class);
        when(afterRskip119.isRskip119()).thenReturn(true);
        DataWord blockHeaderContractAddress = DataWord.valueOf(PrecompiledContracts.BLOCK_HEADER_ADDR.getBytes());
        PrecompiledContract blockHeaderContract1 = precompiledContracts.getContractForAddress(afterRskip119, blockHeaderContractAddress);
        PrecompiledContract blockHeaderContract2 = precompiledContracts.getContractForAddress(afterRskip119, blockHeaderContractAddress);

        Assert.assertNotNull(blockHeaderContract1);
        Assert.assertNotNull(blockHeaderContract2);
        Assert.assertEquals(BlockHeaderContract.class, blockHeaderContract1.getClass());
        Assert.assertEquals(BlockHeaderContract.class, blockHeaderContract2.getClass());
        Assert.assertNotSame(blockHeaderContract1, blockHeaderContract2);
    }
}
