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

import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.junit.Test;

import java.util.Random;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class TransactionResultDTOTest {
    @Test
    public void remascAddressSerialization() {
        RemascTransaction remascTransaction = new RemascTransaction(new Random().nextLong());

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, remascTransaction);
        assertThat(dto.from, is("0x0000000000000000000000000000000000000000"));
        assertThat(dto.r, is(nullValue()));
        assertThat(dto.s, is(nullValue()));
        assertThat(dto.v, is(nullValue()));
    }
}