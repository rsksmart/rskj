/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TxSenderTest {
    @Test
    public void testEquals() {
        TxSender senderA = new TxSender(Hex.decode("aabbccdd"));
        TxSender senderB = new TxSender(Hex.decode("aabbccdd"));
        TxSender senderC = new TxSender(Hex.decode("aabbccddee"));
        TxSender senderD = new TxSender(Hex.decode(""));
        TxSender senderE = new TxSender(Hex.decode("112233"));

        Assert.assertEquals(senderA, senderB);
        Assert.assertNotEquals(senderA, senderC);
        Assert.assertNotEquals(senderA, senderD);
        Assert.assertNotEquals(senderA, senderE);
    }

    @Test
    public void fromTx() {
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender()).thenReturn(Hex.decode("aabb"));

        Assert.assertEquals(new TxSender(Hex.decode("aabb")), TxSender.fromTx(mockedTx));
    }

}
