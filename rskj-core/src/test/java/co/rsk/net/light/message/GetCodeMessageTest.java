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


import org.junit.Before;
import org.junit.Test;

import static co.rsk.net.light.LightClientMessageCodes.GET_CODE;
import static org.ethereum.TestUtils.*;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class GetCodeMessageTest {

    private byte[] blockHash;
    private byte[] address;
    private int id;
    private GetCodeMessage testMessage;

    @Before
    public void setUp() {
        blockHash = randomHash();
        address = randomAddress().getBytes();
        id = 1;
        testMessage = new GetCodeMessage(id, blockHash, address);
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getBlockHash(), blockHash);
        assertArrayEquals(testMessage.getAddress(), address);
        assertEquals(GET_CODE, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        byte[] encoded = testMessage.getEncoded();
        GetCodeMessage getCodeMessage = new GetCodeMessage(encoded);

        assertEquals(id, getCodeMessage.getId());
        assertArrayEquals(blockHash, getCodeMessage.getBlockHash());
        assertArrayEquals(address, getCodeMessage.getAddress());
        assertEquals(GET_CODE, getCodeMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), getCodeMessage.getAnswerMessage());
        assertArrayEquals(encoded, getCodeMessage.getEncoded());
    }
}
