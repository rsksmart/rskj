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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

class SnapBlocksRequestMessageTest {

    private final Block block4Test = new BlockGenerator().getBlock(1);
    private final SnapBlocksRequestMessage underTest = new SnapBlocksRequestMessage(1, block4Test.getNumber());


    @Test
    void getMessageType_returnCorrectMessageType() {
        //given-when
        MessageType messageType = underTest.getMessageType();

        //then
        assertEquals(MessageType.SNAP_BLOCKS_REQUEST_MESSAGE, messageType);
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given default block 4 test

        //when
        byte[] encodedMessage = underTest.getEncodedMessage();

        byte[] expectedEncodedMessage = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(underTest.getId())),
                RLP.encodeList(RLP.encodeBigInteger(BigInteger.ONE)));

        //then
        assertThat(encodedMessage, equalTo(expectedEncodedMessage));
    }

    @Test
    void decodeMessage_returnExpectedMessage() {
        //given default block 4 test
        RLPList encodedRLPList = (RLPList) RLP.decode2(underTest.getEncodedMessage()).get(0);

        //when
        Message decodedMessage = SnapBlocksRequestMessage.decodeMessage(encodedRLPList);

        //then
        assertInstanceOf(SnapBlocksRequestMessage.class, decodedMessage);
        assertThat(underTest.getId(), equalTo(((SnapBlocksRequestMessage) decodedMessage).getId()));
        assertEquals(1, ((SnapBlocksRequestMessage) decodedMessage).getBlockNumber());
    }

    @Test
    void getBlockNumber_returnTheExpectedValue() {
        //given default block 4 test

        //when
        long blockNumber = underTest.getBlockNumber();

        //then
        assertThat(blockNumber, equalTo(block4Test.getNumber()));
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        Block block = new BlockGenerator().getBlock(1);
        SnapBlocksRequestMessage message = new SnapBlocksRequestMessage(1, block.getNumber());
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}
