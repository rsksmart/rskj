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
import co.rsk.pcc.bto.BTOUtils;
import co.rsk.peg.Bridge;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
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
        DataWord bridgeAddress = new DataWord(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        PrecompiledContract bridge = precompiledContracts.getContractForAddress(null, bridgeAddress);

        Assert.assertNotNull(bridge);
        Assert.assertEquals(Bridge.class, bridge.getClass());
    }

    @Test
    public void getBridgeContractTwice() {
        DataWord bridgeAddress = new DataWord(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        PrecompiledContract bridge1 = precompiledContracts.getContractForAddress(null, bridgeAddress);
        PrecompiledContract bridge2 = precompiledContracts.getContractForAddress(null, bridgeAddress);

        Assert.assertNotNull(bridge1);
        Assert.assertNotNull(bridge2);
        Assert.assertNotSame(bridge1, bridge2);
    }

    @Test
    public void getBtoUtilsBeforeRskip106() {
        BlockchainConfig afterRskip106 = mock(BlockchainConfig.class);
        when(afterRskip106.isRskip106()).thenReturn(false);
        DataWord btoUtilsAddress = new DataWord(PrecompiledContracts.BTOUTILS_ADDR.getBytes());
        PrecompiledContract btoUtils = precompiledContracts.getContractForAddress(afterRskip106, btoUtilsAddress);

        Assert.assertNull(btoUtils);
    }

    @Test
    public void getBtoUtilsAfterRskip106() {
        BlockchainConfig afterRskip106 = mock(BlockchainConfig.class);
        when(afterRskip106.isRskip106()).thenReturn(true);
        DataWord btoUtilsAddress = new DataWord(PrecompiledContracts.BTOUTILS_ADDR.getBytes());
        PrecompiledContract btoUtils1 = precompiledContracts.getContractForAddress(afterRskip106, btoUtilsAddress);
        PrecompiledContract btoUtils2 = precompiledContracts.getContractForAddress(afterRskip106, btoUtilsAddress);

        Assert.assertNotNull(btoUtils1);
        Assert.assertNotNull(btoUtils2);
        Assert.assertEquals(BTOUtils.class, btoUtils1.getClass());
        Assert.assertEquals(BTOUtils.class, btoUtils2.getClass());
        Assert.assertNotSame(btoUtils1, btoUtils2);
    }
}
