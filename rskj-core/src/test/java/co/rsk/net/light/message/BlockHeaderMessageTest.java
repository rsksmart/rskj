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
import org.ethereum.core.BlockHeader;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.net.light.LightClientMessageCodes.BLOCK_HEADER;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class BlockHeaderMessageTest {

    private byte[] blockHeaderHash;
    private BlockHeader blockHeader;
    private LCMessageFactory messageFactory;

    @Before
    public void setUp() {
        blockHeaderHash = randomHash();
        byte[] fullBlockHeaderHash = randomHash();
        BlockFactory blockFactory = mock(BlockFactory.class);
        blockHeader = mock(BlockHeader.class);
        messageFactory = new LCMessageFactory(blockFactory);
        when(blockHeader.getEncoded()).thenReturn(blockHeaderHash);
        when(blockHeader.getFullEncoded()).thenReturn(fullBlockHeaderHash);
        when(blockFactory.decodeHeader(fullBlockHeaderHash)).thenReturn(blockHeader);
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        BlockHeaderMessage testMessage = new BlockHeaderMessage(id, blockHeader);

        assertEquals(id, testMessage.getId());
        assertArrayEquals(blockHeaderHash, testMessage.getBlockHeader().getEncoded());
        assertNull(testMessage.getAnswerMessage());
        assertEquals(BLOCK_HEADER, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1;
        createMessageAndAssertEncodeDecode(id);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroId() {
        long id = 0;
        createMessageAndAssertEncodeDecode(id);
    }

    private void createMessageAndAssertEncodeDecode(long id) {
        BlockHeaderMessage testMessage = new BlockHeaderMessage(id, blockHeader);
        byte[] encoded = testMessage.getEncoded();

        BlockHeaderMessage blockHeaderMessage = (BlockHeaderMessage) messageFactory.create(BLOCK_HEADER.asByte(), encoded);

        assertEquals(id, blockHeaderMessage.getId());
        assertArrayEquals(blockHeader.getEncoded(), blockHeaderMessage.getBlockHeader().getEncoded());
        assertEquals(BLOCK_HEADER, blockHeaderMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), blockHeaderMessage.getAnswerMessage());
        assertArrayEquals(encoded, blockHeaderMessage.getEncoded());
    }
}
