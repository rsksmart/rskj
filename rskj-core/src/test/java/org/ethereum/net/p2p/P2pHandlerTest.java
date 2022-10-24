/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.net.p2p;

import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.MessageQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class P2pHandlerTest {

    private P2pHandler p2pHandler;
    private MessageQueue msgQueue;

    @BeforeEach
    void setup() {
        EthereumListener ethereumListener = mock(EthereumListener.class);
        msgQueue = mock(MessageQueue.class);
        p2pHandler = new P2pHandler(ethereumListener, msgQueue, 1000);
    }

    @Test
    void shouldDisconnectIfHelloMessageReceived() {
        EmbeddedChannel channel = new EmbeddedChannel(p2pHandler);
        HelloMessage msg = mock(HelloMessage.class);
        when(msg.getCommand()).thenReturn(P2pMessageCodes.HELLO);

        channel.writeInbound(msg);

        verify(msgQueue, times(1)).disconnect();
    }

    @Test
    void channelInactive_shouldCallKillTimers() throws Exception {
        EthereumListener ethereumListener = mock(EthereumListener.class);
        P2pHandler p2pHandlerSpy = spy(new P2pHandler(ethereumListener, msgQueue, 1000));

        doNothing().when(p2pHandlerSpy).killTimers();

        p2pHandlerSpy.channelInactive(null);

        verify(p2pHandlerSpy, times(1)).killTimers();
    }
}
