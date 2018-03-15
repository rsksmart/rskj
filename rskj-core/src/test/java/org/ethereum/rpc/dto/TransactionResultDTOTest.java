/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc.dto;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionResultDTOTest {
    @Test
    public void remascAddressSerialization() {
        Block b = mock(Block.class);
        Integer index = 42;
        Transaction tx = mock(Transaction.class);

        when(tx.getHash()).thenReturn(new Keccak256(TestUtils.randomBytes(32)));
        when(tx.getSender()).thenReturn(RemascTransaction.REMASC_ADDRESS);
        when(tx.getReceiveAddress()).thenReturn(TestUtils.randomAddress());
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(42));
        when(tx.getValue()).thenReturn(Coin.ZERO);
        when(tx.getData()).thenReturn(TestUtils.randomBytes(2));

        TransactionResultDTO dto = new TransactionResultDTO(b, index, tx);
        assertThat(dto.from, is("0x0000000000000000000000000000000000000000"));
    }
}