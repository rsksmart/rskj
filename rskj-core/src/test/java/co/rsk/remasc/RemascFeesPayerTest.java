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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author martin.medina
 */
public class RemascFeesPayerTest {

    @Test
    public void payMiningFees() {

        // Setup objects
        Repository repositoryMock = Mockito.mock(Repository.class);
        RemascFeesPayer feesPayer = new RemascFeesPayer(repositoryMock, PrecompiledContracts.REMASC_ADDR);

        byte[] blockHash = { 0x1, 0x2 };
        Coin value = Coin.valueOf(7L);
        RskAddress toAddress = new RskAddress("6c386a4b26f73c802f34673f7248bb118f97424a");
        List<LogInfo> logs = new ArrayList<>();

        // Do call
        feesPayer.payMiningFees(blockHash, value, toAddress, logs);

        Assert.assertEquals(1, logs.size());

        // Assert address that made the log
        LogInfo result = logs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.REMASC_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(2, result.getTopics().size());
        Assert.assertEquals("000000000000000000000000000000006d696e696e675f6665655f746f706963", result.getTopics().get(0).toString());
        Assert.assertEquals("0000000000000000000000006c386a4b26f73c802f34673f7248bb118f97424a", result.getTopics().get(1).toString());

        // Assert log data
        Assert.assertNotNull(result.getData());
        List<RLPElement> rlpData = RLP.decode2(result.getData());
        Assert.assertEquals(1 , rlpData.size());
        RLPList dataList = (RLPList)rlpData.get(0);
        Assert.assertEquals(2, dataList.size());
        Assert.assertArrayEquals(blockHash, dataList.get(0).getRLPData());
        Assert.assertEquals(value, RLP.parseCoin(dataList.get(1).getRLPData()));

        // Assert repository calls are made right
        verify(repositoryMock, times(1)).addBalance(PrecompiledContracts.REMASC_ADDR, value.negate());
        verify(repositoryMock, times(1)).addBalance(toAddress, value);
    }
}
