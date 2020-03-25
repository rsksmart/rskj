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

import static co.rsk.net.light.LightClientMessageCodes.CODE;
import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;

public class CodeMessageTest {

    private CodeMessage testMessage;
    private byte[] codeHash;
    private int id;

    @Before

    public void setUp() {
        id = 1;
        codeHash = randomHash();
        testMessage = new CodeMessage(id, codeHash);
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        assertEquals(id, testMessage.getId());
        assertArrayEquals(codeHash, testMessage.getCodeHash());
        assertEquals(CODE, testMessage.getCommand());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        byte[] encoded = testMessage.getEncoded();
        CodeMessage codeMessage = new CodeMessage(encoded);

        assertEquals(id, codeMessage.getId());
        assertArrayEquals(codeHash, codeMessage.getCodeHash());
        assertEquals(CODE, codeMessage.getCommand());
        assertEquals(testMessage.getAnswerMessage(), codeMessage.getAnswerMessage());
        assertArrayEquals(encoded, codeMessage.getEncoded());
    }
}
