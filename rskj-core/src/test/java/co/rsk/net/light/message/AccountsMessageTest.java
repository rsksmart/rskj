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

public class AccountsMessageTest {

    private byte[] merkleInclusionProof;
    private byte[] codeHash;
    private byte[] storageRoot;

    @Before
    public void setUp() {
        merkleInclusionProof = HashUtil.randomHash();
        codeHash = HashUtil.randomHash();
        storageRoot = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        long nonce = 123;
        long balance = 100;
        AccountsMessage testMessage = new AccountsMessage(id, merkleInclusionProof,
                nonce, balance,
                codeHash, storageRoot);
        assertEquals(LightClientMessageCodes.ACCOUNTS, testMessage.getCommand());
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getMerkleInclusionProof(), merkleInclusionProof);
        assertEquals(testMessage.getNonce(), nonce);
        assertEquals(testMessage.getBalance(), balance);
        assertArrayEquals(testMessage.getCodeHash(), codeHash);
        assertArrayEquals(testMessage.getStorageRoot(), storageRoot);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        long id = 1;
        long nonce = 123;
        long balance = 100;
        createMessageAndAssertEncodeDecode(id, nonce, balance);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrectWithZeroParameters() {
        long id = 0;
        long nonce = 0;
        long balance = 0;
        createMessageAndAssertEncodeDecode(id, nonce, balance);
    }

    private void createMessageAndAssertEncodeDecode(long id, long nonce, long balance) {
        AccountsMessage testMessage = new AccountsMessage(id, merkleInclusionProof,
                nonce, balance,
                codeHash, storageRoot);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.ACCOUNTS.asByte();
        AccountsMessage message = (AccountsMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getId(), message.getId());
        assertArrayEquals(testMessage.getMerkleInclusionProof(), message.getMerkleInclusionProof());
        assertEquals(testMessage.getNonce(), message.getNonce());
        assertEquals(testMessage.getBalance(), message.getBalance());
        assertArrayEquals(testMessage.getCodeHash(), message.getCodeHash());
        assertArrayEquals(testMessage.getStorageRoot(), message.getStorageRoot());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }
}
