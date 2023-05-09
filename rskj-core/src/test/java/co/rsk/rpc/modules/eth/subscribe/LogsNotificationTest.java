/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class LogsNotificationTest {

    private static final String QUANTITY_JSON_HEX = HexUtils.toQuantityJsonHex(42);

    private LogsNotification logsNotification;
    private Block block;
    private Transaction transaction;
    private LogInfo logInfo;

    @BeforeEach
    void createLogNotification() {
        block = mock(Block.class);
        transaction = mock(Transaction.class);
        logInfo = mock(LogInfo.class);
        this.logsNotification = new LogsNotification(
                logInfo,
                block,
                42,
                transaction,
                42,
                true
        );
    }

    @Test
    void getLogIndex() {
        assertThat(logsNotification.getLogIndex(), is(QUANTITY_JSON_HEX));
    }

    @Test
    void getBlockNumber() {
        doReturn(42L).when(block).getNumber();
        assertThat(logsNotification.getBlockNumber(), is(QUANTITY_JSON_HEX));
    }

    @Test
    void getBlockHash() {
        Keccak256 blockHash = TestUtils.generateHash("blockHash");
        doReturn(blockHash).when(block).getHash();
        doCallRealMethod().when(block).getHashJsonString();
        assertThat(logsNotification.getBlockHash(), is(HexUtils.toUnformattedJsonHex(blockHash.getBytes())));
    }

    @Test
    void getTransactionHash() {
        Keccak256 transactionHash = TestUtils.generateHash("transactionHash");
        doReturn(transactionHash).when(transaction).getHash();
        assertThat(logsNotification.getTransactionHash(), is(HexUtils.toUnformattedJsonHex(transactionHash.getBytes())));

    }

    @Test
    void getTransactionIndex() {
        assertThat(logsNotification.getTransactionIndex(), is(QUANTITY_JSON_HEX));
    }

    @Test
    void getAddress() {
        byte[] logSender = TestUtils.generateAddress("logSender").getBytes();
        doReturn(logSender).when(logInfo).getAddress();
        assertThat(logsNotification.getAddress(), is(HexUtils.toJsonHex(logSender)));
    }

    @Test
    void getData() {
        byte[] logData = TestUtils.generateBytes(LogsNotificationTest.class,"logData",256);
        doReturn(logData).when(logInfo).getData();
        assertThat(logsNotification.getData(), is(HexUtils.toJsonHex(logData)));
    }

    @Test
    void getTopics() {
        List<DataWord> logTopics = IntStream.range(0, 128).mapToObj(i -> TestUtils.generateDataWord()).collect(Collectors.toList());
        doReturn(logTopics).when(logInfo).getTopics();
        for (int i = 0; i < logTopics.size(); i++) {
            assertThat(logsNotification.getTopics().get(i), is(HexUtils.toJsonHex(logTopics.get(i).getData())));
        }
    }
}
