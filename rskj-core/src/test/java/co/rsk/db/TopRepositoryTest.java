/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.Random;

public class TopRepositoryTest {
    private Random random = new Random();

    @Test
    public void createWithTrie() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void createAccount() {
        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        AccountState accountState = repository.createAccount(address);

        Assert.assertNotNull(accountState);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertSame(accountState, result);
    }
}
