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
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.core.Repository;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author martin.medina
 */
class RemascFeesPayerTest {

    @Test
    void payMiningFees() {

        // Setup objects
        Repository repositoryMock = Mockito.mock(Repository.class);
        RemascFeesPayer feesPayer = new RemascFeesPayer(repositoryMock, PrecompiledContracts.REMASC_ADDR);

        byte[] blockHash = { 0x1, 0x2 };
        Coin value = Coin.valueOf(7L);
        RskAddress toAddress = new RskAddress("6c386a4b26f73c802f34673f7248bb118f97424a");
        List<LogInfo> logs = new ArrayList<>();

        // Do call
        feesPayer.payMiningFees(blockHash, value, toAddress, logs);

        Assertions.assertEquals(1, feesPayer.getSubtraces().size());

        ProgramSubtrace subtrace = feesPayer.getSubtraces().get(0);

        Assertions.assertEquals(DataWord.valueOf(PrecompiledContracts.REMASC_ADDR.getBytes()), subtrace.getInvokeData().getCallerAddress());
        Assertions.assertEquals(DataWord.valueOf(toAddress.getBytes()), subtrace.getInvokeData().getOwnerAddress());
        Assertions.assertEquals(DataWord.valueOf(value.getBytes()), subtrace.getInvokeData().getCallValue());

        Assertions.assertEquals(1, logs.size());

        // Assert address that made the log
        LogInfo result = logs.get(0);
        Assertions.assertArrayEquals(PrecompiledContracts.REMASC_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assertions.assertEquals(2, result.getTopics().size());
        Assertions.assertEquals("000000000000000000000000000000006d696e696e675f6665655f746f706963", result.getTopics().get(0).toString());
        Assertions.assertEquals("0000000000000000000000006c386a4b26f73c802f34673f7248bb118f97424a", result.getTopics().get(1).toString());

        // Assert log data
        Assertions.assertNotNull(result.getData());
        List<RLPElement> rlpData = RLP.decode2(result.getData());
        Assertions.assertEquals(1 , rlpData.size());
        RLPList dataList = (RLPList)rlpData.get(0);
        Assertions.assertEquals(2, dataList.size());
        Assertions.assertArrayEquals(blockHash, dataList.get(0).getRLPData());
        Assertions.assertEquals(value, RLP.parseCoin(dataList.get(1).getRLPData()));

        // Assert repository calls are made right
        verify(repositoryMock, times(1)).addBalance(PrecompiledContracts.REMASC_ADDR, value.negate());
        verify(repositoryMock, times(1)).addBalance(toAddress, value);
    }
}
