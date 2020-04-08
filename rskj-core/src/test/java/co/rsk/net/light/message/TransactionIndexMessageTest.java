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

package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class TransactionIndexMessageTest {

    private long id;
    private long blockNumber;
    private byte[] blockHash;
    private long txIndex;
    private BlockFactory blockFactory;

    @Before
    public void setUp() {
        id = 1L;
        blockNumber = 1000L;
        blockHash = HashUtil.randomHash();
        txIndex = 1234L;
        blockFactory = mock(BlockFactory.class);
    }

    @Test
    public void createMessage() {
        TransactionIndexMessage message = new TransactionIndexMessage(id, blockNumber, blockHash, txIndex);

        assertEquals(LightClientMessageCodes.TRANSACTION_INDEX, message.getCommand());
        assertEquals(id, message.getId());
        assertEquals(blockNumber, message.getBlockNumber());
        assertArrayEquals(blockHash, message.getBlockHash());
        assertEquals(txIndex, message.getTransactionIndex());
        assertNull(message.getAnswerMessage());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        TransactionIndexMessage testMessage = new TransactionIndexMessage(id, blockNumber, blockHash, txIndex);
        byte[] encoded = testMessage.getEncoded();

        byte code = LightClientMessageCodes.TRANSACTION_INDEX.asByte();

        LCMessageFactory lcMessageFactory = new LCMessageFactory(blockFactory);
        TransactionIndexMessage message = (TransactionIndexMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());

        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getBlockNumber(), message.getBlockNumber());
        assertArrayEquals(testMessage.getBlockHash(), message.getBlockHash());
        assertEquals(testMessage.getTransactionIndex(), message.getTransactionIndex());

    }
}