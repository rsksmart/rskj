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

import co.rsk.config.TestSystemProperties;
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

class SnapStatusRequestMessageTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    void getMessageType_returnCorrectMessageType() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage(1);

        //when
        MessageType messageType = message.getMessageType();

        //then
        assertThat(messageType, equalTo(MessageType.SNAP_STATUS_REQUEST_MESSAGE));
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage(1);
        byte[] expectedEncodedMessage = RLP.encodeList(RLP.encodeBigInteger(BigInteger.valueOf(1)), RLP.encodedEmptyList());
        //when
        byte[] encodedMessage = message.getEncodedMessage();

        //then
        assertThat(encodedMessage, equalTo(expectedEncodedMessage));
    }

    @Test
    void decodeMessage_returnExpectedMessage() {
        //given default block 4 test
        SnapStatusRequestMessage message = new SnapStatusRequestMessage(111);
        RLPList encodedRLPList = (RLPList) RLP.decode2(message.getEncodedMessage()).get(0);

        //when
        Message decodedMessage = SnapStatusRequestMessage.decodeMessage(blockFactory, encodedRLPList);

        //then
        assertInstanceOf(SnapStatusRequestMessage.class, decodedMessage);
        assertEquals(111, ((SnapStatusRequestMessage) decodedMessage).getId());
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage(1);
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}
