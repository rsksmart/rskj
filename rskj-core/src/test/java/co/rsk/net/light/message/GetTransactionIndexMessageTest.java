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

public class GetTransactionIndexMessageTest {

    private byte[] txHash;
    private int id;

    @Before
    public void setUp() {
        txHash = HashUtil.randomHash();
        id = 1;
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        GetTransactionIndexMessage testMessage = new GetTransactionIndexMessage(id, txHash);
        assertEquals(LightClientMessageCodes.GET_TRANSACTION_INDEX, testMessage.getCommand());
        assertArrayEquals(testMessage.getTxHash(), txHash);
        assertEquals(testMessage.getId(), id);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {

        GetTransactionIndexMessage testMessage = new GetTransactionIndexMessage(id, txHash);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.GET_TRANSACTION_INDEX.asByte();
        GetTransactionIndexMessage message = (GetTransactionIndexMessage) lcMessageFactory.create(code, encoded);

        assertArrayEquals(testMessage.getTxHash(), message.getTxHash());
        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }

}
