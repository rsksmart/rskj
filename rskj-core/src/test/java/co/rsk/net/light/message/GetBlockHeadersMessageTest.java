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

public class GetBlockHeadersMessageTest {

    private byte[] blockHash;
    private LCMessageFactory messageFactory;

    @Before
    public void setUp() {
        blockHash = randomHash();
        messageFactory = new LCMessageFactory(mock(BlockFactory.class));
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        int max = 2;
        int skip = 5;
        GetBlockHeadersMessage testMessage = new GetBlockHeadersMessage(id, blockHash, max, skip, true);

        assertEquals(id, testMessage.getId());
        assertArrayEquals(blockHash, testMessage.getBlockHash());
        assertEquals(max, testMessage.getMax());
        assertEquals(skip, testMessage.getSkip());
        assertTrue(testMessage.isReverse());
        assertEquals(BlockHeadersMessage.class, testMessage.getAnswerMessage());
        assertEquals(GET_BLOCK_HEADER, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1;
        int max = 2;
        int skip = 3;

        createMessageAndAssertEncodeDecode(id, max, skip, true);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroParameters() {
        long id = 0;
        int max = 0;
        int skip = 0;
        createMessageAndAssertEncodeDecode(id, max, skip, false);
    }

    private void createMessageAndAssertEncodeDecode(long id, int max, int skip, boolean reverse) {
        GetBlockHeadersMessage testMessage = new GetBlockHeadersMessage(id, blockHash, max, skip, reverse);
        byte[] encoded = testMessage.getEncoded();
        GetBlockHeadersMessage getBlockHeadersMessage = (GetBlockHeadersMessage) messageFactory.create(GET_BLOCK_HEADER.asByte(), encoded);

        assertEquals(id, getBlockHeadersMessage.getId());
        assertArrayEquals(blockHash, getBlockHeadersMessage.getBlockHash());
        assertEquals(max, getBlockHeadersMessage.getMax());
        assertEquals(GET_BLOCK_HEADER, getBlockHeadersMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), getBlockHeadersMessage.getAnswerMessage());
        assertArrayEquals(encoded, getBlockHeadersMessage.getEncoded());
    }
}