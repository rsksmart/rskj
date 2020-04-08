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

import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.net.light.LightClientMessageCodes.GET_BLOCK_HEADER;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GetBlockHeaderMessageTest {

    private byte[] blockHash;
    private int id;
    private GetBlockHeaderMessage testMessage;
    private LCMessageFactory messageFactory;

    @Before
    public void setUp() {
        id = 1;
        blockHash = randomHash();
        messageFactory = new LCMessageFactory(mock(BlockFactory.class));
        testMessage = new GetBlockHeaderMessage(id, blockHash);
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        assertEquals(id, testMessage.getId());
        assertArrayEquals(blockHash, testMessage.getBlockHash());
        assertEquals(BlockHeaderMessage.class, testMessage.getAnswerMessage());
        assertEquals(GET_BLOCK_HEADER, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        byte[] encoded = testMessage.getEncoded();
        GetBlockHeaderMessage getBlockHeaderMessage = (GetBlockHeaderMessage) messageFactory.create(GET_BLOCK_HEADER.asByte(), encoded);

        assertEquals(id, getBlockHeaderMessage.getId());
        assertArrayEquals(blockHash, getBlockHeaderMessage.getBlockHash());
        assertEquals(GET_BLOCK_HEADER, getBlockHeaderMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), getBlockHeaderMessage.getAnswerMessage());
        assertArrayEquals(encoded, getBlockHeaderMessage.getEncoded());
    }
}