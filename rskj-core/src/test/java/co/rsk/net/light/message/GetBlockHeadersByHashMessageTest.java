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

import static co.rsk.net.light.LightClientMessageCodes.GET_BLOCK_HEADER_BY_HASH;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GetBlockHeadersByHashMessageTest {

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
        int maxAmountOfHeaders = 2;
        int skip = 5;
        GetBlockHeadersByHashMessage testMessage = new GetBlockHeadersByHashMessage(id, blockHash, maxAmountOfHeaders, skip, true);

        assertEquals(id, testMessage.getId());
        assertArrayEquals(blockHash, testMessage.getStartBlockHash());
        assertEquals(maxAmountOfHeaders, testMessage.getMaxAmountOfHeaders());
        assertEquals(skip, testMessage.getSkip());
        assertTrue(testMessage.isReverse());
        assertEquals(BlockHeadersMessage.class, testMessage.getAnswerMessage());
        assertEquals(GET_BLOCK_HEADER_BY_HASH, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1;
        int maxAmountOfHeaders = 2;
        int skip = 3;

        createMessageAndAssertEncodeDecode(id, maxAmountOfHeaders, skip, true);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroParameters() {
        long id = 0;
        int maxAmountOfHeaders = 0;
        int skip = 0;
        createMessageAndAssertEncodeDecode(id, maxAmountOfHeaders, skip, false);
    }

    private void createMessageAndAssertEncodeDecode(long id, int maxAmountOfHeaders, int skip, boolean reverse) {
        GetBlockHeadersByHashMessage testMessage = new GetBlockHeadersByHashMessage(id, blockHash, maxAmountOfHeaders, skip, reverse);
        byte[] encoded = testMessage.getEncoded();
        GetBlockHeadersByHashMessage getBlockHeadersByHashMessage = (GetBlockHeadersByHashMessage) messageFactory.create(GET_BLOCK_HEADER_BY_HASH.asByte(), encoded);

        assertEquals(id, getBlockHeadersByHashMessage.getId());
        assertArrayEquals(blockHash, getBlockHeadersByHashMessage.getStartBlockHash());
        assertEquals(maxAmountOfHeaders, getBlockHeadersByHashMessage.getMaxAmountOfHeaders());
        assertEquals(GET_BLOCK_HEADER_BY_HASH, getBlockHeadersByHashMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), getBlockHeadersByHashMessage.getAnswerMessage());
        assertArrayEquals(encoded, getBlockHeadersByHashMessage.getEncoded());
    }
}