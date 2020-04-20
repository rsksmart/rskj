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
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.Transaction;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;

import static co.rsk.net.light.LightClientMessageCodes.BLOCK_BODY;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class BlockBodyMessageTest {

    private LinkedList<Transaction> transactionList;
    private LinkedList<BlockHeader> uncleList;
    private BlockFactory blockFactory;

    @Before
    public void setup() {
        transactionList = createTransactionList();
        ActivationConfig activationConfig = mock(ActivationConfig.class);
        blockFactory = new BlockFactory(activationConfig);
        uncleList = getUncleList();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        long id = 1;
        BlockBodyMessage testMessage = new BlockBodyMessage(id, transactionList, uncleList);
        assertEquals(BLOCK_BODY, testMessage.getCommand());
        assertEquals(testMessage.getTransactions(), transactionList);
        assertEquals(testMessage.getUncles(), uncleList);
        assertEquals(testMessage.getId(), id);
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
        LCMessageFactory lcMessageFactory = new LCMessageFactory(blockFactory);
        BlockBodyMessage testMessage = new BlockBodyMessage(id, transactionList, uncleList);
        byte[] encoded = testMessage.getEncoded();


        BlockBodyMessage message = (BlockBodyMessage) lcMessageFactory.create(BLOCK_BODY.asByte(), encoded);

        assertEquals(testMessage.getTransactions(), message.getTransactions());
        assertEquals(testMessage.getUncles().size(), message.getUncles().size());
        for (int i = 0; i < testMessage.getUncles().size(); i++) {
            assertArrayEquals(testMessage.getUncles().get(i).getFullEncoded(), message.getUncles().get(i).getFullEncoded());
        }
        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }

    private LinkedList<BlockHeader> getUncleList() {
        BlockHeaderBuilder blockHeaderBuilder = blockFactory.getBlockHeaderBuilder();
        BlockHeader blockHeader = blockHeaderBuilder.setNumber(1).build();
        LinkedList<BlockHeader> uncleList = new LinkedList<>();
        uncleList.add(blockHeader);
        return uncleList;
    }

    private LinkedList<Transaction> createTransactionList() {
        Transaction tx1 = new Transaction( new byte[]{0x0}, null, null, null, null, null);
        Transaction tx2 = new Transaction( new byte[]{0x1}, null, null, null, null, null);

        LinkedList<Transaction> transactions = new LinkedList<>();
        transactions.add(tx1);
        transactions.add(tx2);

        return transactions;
    }
}