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

import static co.rsk.net.light.LightClientMessageCodes.GET_BLOCK_HEADER_BY_NUMBER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GetBlockHeadersByNumberMessageTest {

    private long blockNumber;
    private LCMessageFactory messageFactory;

    @Before
    public void setUp() {
        blockNumber = 5L;
        messageFactory = new LCMessageFactory(mock(BlockFactory.class));
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        int maxAmountOfHeaders = 2;
        int skip = 5;
        GetBlockHeadersByNumberMessage testMessage = new GetBlockHeadersByNumberMessage(id, blockNumber, maxAmountOfHeaders, skip, true);

        assertEquals(id, testMessage.getId());
        assertEquals(blockNumber, testMessage.getStartBlockNumber());
        assertEquals(maxAmountOfHeaders, testMessage.getMaxAmountOfHeaders());
        assertEquals(skip, testMessage.getSkip());
        assertTrue(testMessage.isReverse());
        assertEquals(BlockHeadersMessage.class, testMessage.getAnswerMessage());
        assertEquals(GET_BLOCK_HEADER_BY_NUMBER, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1;
        int max = 2;
        int skip = 3;

        createMessageAndAssertEncodeDecode(id, max, skip, true, blockNumber);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroParameters() {
        long id = 0;
        int maxAmountOfHeaders = 0;
        int skip = 0;
        createMessageAndAssertEncodeDecode(id, maxAmountOfHeaders, skip, false, 0);
    }

    private void createMessageAndAssertEncodeDecode(long id, int maxAmountOfHeaders, int skip, boolean reverse, long blockNumber) {
        GetBlockHeadersByNumberMessage testMessage = new GetBlockHeadersByNumberMessage(id, blockNumber, maxAmountOfHeaders, skip, reverse);
        byte[] encoded = testMessage.getEncoded();
        GetBlockHeadersByNumberMessage getBlockHeadersByNumberMessageMessage = (GetBlockHeadersByNumberMessage) messageFactory.create(GET_BLOCK_HEADER_BY_NUMBER.asByte(), encoded);

        assertEquals(id, getBlockHeadersByNumberMessageMessage.getId());
        assertEquals(blockNumber, getBlockHeadersByNumberMessageMessage.getStartBlockNumber());
        assertEquals(maxAmountOfHeaders, getBlockHeadersByNumberMessageMessage.getMaxAmountOfHeaders());
        assertEquals(GET_BLOCK_HEADER_BY_NUMBER, getBlockHeadersByNumberMessageMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), getBlockHeadersByNumberMessageMessage.getAnswerMessage());
        assertArrayEquals(encoded, getBlockHeadersByNumberMessageMessage.getEncoded());
    }
}