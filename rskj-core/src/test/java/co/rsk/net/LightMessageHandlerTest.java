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

package co.rsk.net;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.LightMessageHandler;
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.message.GetAccountsMessage;
import io.netty.channel.ChannelHandlerContext;
import org.bouncycastle.util.Arrays;
import org.ethereum.core.AccountState;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.ethereum.TestUtils.randomHash;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LightMessageHandlerTest {

    private LightMessageHandler lightMessageHandler;

    private LightPeer lightPeer1;
    private LightPeer lightPeer2;
    private ChannelHandlerContext ctx1;
    private ChannelHandlerContext ctx2;
    private LightClientHandler lightClientHandler1;
    private LightClientHandler lightClientHandler2;

    private GetAccountsMessage m1;
    private GetAccountsMessage m2;

    private LightClientTestUtils lightClientTestUtils;

    @Before
    public void setUp() {
        lightClientTestUtils = new LightClientTestUtils();

        lightPeer1 = lightClientTestUtils.createPeer();
        lightPeer2 = lightClientTestUtils.createPeer();

        lightClientHandler1 = lightClientTestUtils.generateLightClientHandler(lightPeer1);
        lightClientHandler2 = lightClientTestUtils.generateLightClientHandler(lightPeer2);

        ctx1 = lightClientTestUtils.hookLightPeerToCtx(lightPeer1, lightClientHandler1);
        ctx2 = lightClientTestUtils.hookLightPeerToCtx(lightPeer2, lightClientHandler2);

        lightMessageHandler = new LightMessageHandler(lightClientTestUtils.getLightProcessor(),
                lightClientTestUtils.getLightSyncProcessor());

        byte[] address1 = randomHash().getBytes();
        address1 = Arrays.copyOfRange(address1, 12, address1.length);
        m1 = new GetAccountsMessage(4, randomHash().getBytes(), address1);

        byte[] address2 = randomHash().getBytes();
        address2 = Arrays.copyOfRange(address2, 12, address2.length);
        m2 = new GetAccountsMessage(123, randomHash().getBytes(), address2);
    }

    /**
     * We check that, given a random message,
     * the processing for that message is called
     */

    @Test
    public void lightMessageHandlerHandlesAMessageCorrectly() {
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        assertEquals(1,lightMessageHandler.getMessageQueueSize());
        lightMessageHandler.handleMessage();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerHandlesTwoMessagesCorrectly() {
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.postMessage(lightPeer1, m2, ctx1, lightClientHandler1);
        assertEquals(2,lightMessageHandler.getMessageQueueSize());
        lightMessageHandler.handleMessage();
        lightMessageHandler.handleMessage();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerHandlesMessagesFromTwoPeersCorrectly() {
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.postMessage(lightPeer2, m2, ctx2, lightClientHandler2);

        assertEquals(2,lightMessageHandler.getMessageQueueSize());

        lightMessageHandler.handleMessage();
        lightMessageHandler.handleMessage();

        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerServicesCorrectlyHandlesMessages() throws InterruptedException {
        lightMessageHandler.start();
        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.postMessage(lightPeer1, m2, ctx1, lightClientHandler1);

        Thread.sleep(500);

        lightMessageHandler.stop();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerHandlesAMessageCorrectlyAndResponseIsSent() {
        Keccak256 codeHash = randomHash();
        byte[] storageRoot = randomHash().getBytes();
        AccountState state = new AccountState(BigInteger.ONE, new Coin(new byte[] {0x10}));

        lightClientTestUtils.includeAccount(new Keccak256(m1.getBlockHash()), m1.getAddressHash(),
                state, codeHash, storageRoot);

        lightMessageHandler.postMessage(lightPeer1, m1, ctx1, lightClientHandler1);
        lightMessageHandler.handleMessage();

        assertEquals(0,lightMessageHandler.getMessageQueueSize());
        verify(lightPeer1, times(1)).sendMessage(any());
    }
}
