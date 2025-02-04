/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SnapStateChunkResponseMessageTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    void getMessageType_returnCorrectMessageType() {
        //given
        Block block = new BlockGenerator().getBlock(1);
        long id4Test = 42L;
        String trieValue = "any random data";
        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValue.getBytes(), block.getNumber(), 0L, 0L, true);

        //when
        MessageType messageType = message.getMessageType();

        //then
        assertThat(messageType, equalTo(MessageType.SNAP_STATE_CHUNK_RESPONSE_MESSAGE));
    }

    @Test
    void givenParameters4Test_assureExpectedValues() {
        //given
        Block block = new BlockGenerator().getBlock(1);
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;

        //when
        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, block.getNumber(), from, to, complete);

        //then
        assertEquals(id4Test, message.getId());
        assertEquals(trieValueBytes, message.getChunkOfTrieKeyValue());
        assertEquals(block.getNumber(),message.getBlockNumber());
        assertEquals(from,message.getFrom());
        assertEquals(to,message.getTo());
        assertEquals(complete,message.isComplete());
    }


    @Test
    void getEncodedMessageWithoutId_returnExpectedByteArray() {
        //given
        long blockNumber = 1L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;

        byte[] expectedEncodedMessage = RLP.encodeList(
                RLP.encodeElement(trieValueBytes),
                RLP.encodeBigInteger(BigInteger.valueOf(blockNumber)),
                RLP.encodeBigInteger(BigInteger.valueOf(from)),
                RLP.encodeBigInteger(BigInteger.valueOf(to)),
                RLP.encodeInt(complete ? 1 : 0));

        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);

        //when
        byte[] encodedMessage = message.getEncodedMessageWithoutId();

        //then
        assertThat(encodedMessage, equalTo(expectedEncodedMessage));
    }

    @Test
    void getEncodedMessageWithId_returnExpectedByteArray() {
        //given
        long blockNumber = 1L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;

        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);
        byte[] expectedEncodedMessage = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(id4Test)), message.getEncodedMessageWithoutId());

        //when
        byte[] encodedMessage = message.getEncodedMessage();

        //then
        assertArrayEquals(encodedMessage, expectedEncodedMessage);
    }

    @Test
    void decodeMessage_returnExpectedMessage() {
        //given default block 4 test
        long blockNumber = 111L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = false;

        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);
        RLPList encodedRLPList = (RLPList) RLP.decode2(message.getEncodedMessage()).get(0);

        //when
        Message decodedMessage = SnapStateChunkResponseMessage.decodeMessage(blockFactory, encodedRLPList);

        //then
        assertInstanceOf(SnapStateChunkResponseMessage.class, decodedMessage);
        assertEquals(id4Test,((SnapStateChunkResponseMessage) decodedMessage).getId());
        assertEquals(from,((SnapStateChunkResponseMessage) decodedMessage).getFrom());
        assertEquals(to,((SnapStateChunkResponseMessage) decodedMessage).getTo());
        assertEquals(blockNumber,((SnapStateChunkResponseMessage) decodedMessage).getBlockNumber());
        assertEquals(complete, ((SnapStateChunkResponseMessage) decodedMessage).isComplete());
        assertThat(trieValueBytes, is(((SnapStateChunkResponseMessage) decodedMessage).getChunkOfTrieKeyValue()));
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        long blockNumber = 1L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;
        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}
