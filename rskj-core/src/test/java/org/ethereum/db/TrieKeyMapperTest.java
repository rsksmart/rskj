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

package org.ethereum.db;

import co.rsk.core.RskAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class TrieKeyMapperTest {

    private static final int BATCH_TEST = 500;
    private TrieKeyMapper trieKeyMapper;

    @Before
    public void setup() {
        trieKeyMapper = spy(new TrieKeyMapper());
    }

    @Test
    public void getAccountKey_new() {
        RskAddress address = new RskAddress("1000000000000000000000000000000000000000");
        this.trieKeyMapper.getAccountKey(address);
        verify(this.trieKeyMapper, times(1)).mapRskAddressToKey(eq(address));
    }

    @Test
    public void getAccountKey_fromCache() {
        RskAddress address = new RskAddress("1000000000000000000000000000000000000001");

        byte[] accountKey = this.trieKeyMapper.getAccountKey(address);
        verify(this.trieKeyMapper, times(1)).mapRskAddressToKey(eq(address));

        byte[] accountKeyCache = this.trieKeyMapper.getAccountKey(address);
        verify(this.trieKeyMapper, times(1)).mapRskAddressToKey(eq(address));
        Assert.assertArrayEquals("Account key diff from diff calls.", accountKey, accountKeyCache);

    }

    @Test
    public void getAccountKey_fromCache_multipleKeys() {
        String addressPrefix = "1000000000000000000000000000000000000";

        int offset = 100;
        for (int i = offset; i < BATCH_TEST + offset; i++) {

            RskAddress address = new RskAddress(addressPrefix + i);
            byte[] accountKey = this.trieKeyMapper.getAccountKey(address);
            verify(this.trieKeyMapper, times(1)).mapRskAddressToKey(eq(address));

            byte[] accountKeyCache = this.trieKeyMapper.getAccountKey(address);
            verify(this.trieKeyMapper, times(1)).mapRskAddressToKey(eq(address));
            Assert.assertArrayEquals("Account key diff from diff calls.", accountKey, accountKeyCache);
        }

        clearInvocations(this.trieKeyMapper);

        for (int i = offset; i < BATCH_TEST + offset; i++) {
            RskAddress address = new RskAddress(addressPrefix + i);
            byte[] accountKey = this.trieKeyMapper.getAccountKey(address);
            verify(this.trieKeyMapper, times(0)).mapRskAddressToKey(eq(address));
            Assert.assertNotNull("Shouldnt return null value from cache.", accountKey);
        }

    }
}
