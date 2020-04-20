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

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class StorageMessageTest {

    private byte[] merkleInclusionProof;
    private byte[] storageValue;

    @Before
    public void setUp() {
        merkleInclusionProof = HashUtil.randomHash();
        storageValue = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        StorageMessage testMessage = new StorageMessage(id, merkleInclusionProof, storageValue);
        assertEquals(LightClientMessageCodes.STORAGE, testMessage.getCommand());
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getMerkleInclusionProof(), merkleInclusionProof);
        assertArrayEquals(testMessage.getStorageValue(), storageValue);
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
        StorageMessage testMessage = new StorageMessage(id, merkleInclusionProof, storageValue);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.STORAGE.asByte();
        StorageMessage message = (StorageMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getId(), message.getId());
        assertArrayEquals(testMessage.getMerkleInclusionProof(), message.getMerkleInclusionProof());
        assertArrayEquals(testMessage.getStorageValue(), message.getStorageValue());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }
}
