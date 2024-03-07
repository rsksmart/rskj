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

import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

class SnapStatusRequestMessageTest {
    @Test
    void getMessageType_returnCorrectMessageType() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();

        //when
        MessageType messageType = message.getMessageType();

        //then
        assertThat(messageType, equalTo(MessageType.SNAP_STATUS_REQUEST_MESSAGE));
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();
        byte[] expectedEncodedMessage = RLP.encodedEmptyList();
        //when
        byte[] encodedMessage = message.getEncodedMessage();

        //then
        assertThat(encodedMessage, equalTo(expectedEncodedMessage));
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}