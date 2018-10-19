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

package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.*;
import co.rsk.trie.MutableSubtrie;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;

import static org.ethereum.crypto.Keccak256Helper.keccak256;

/**
 * @author Sergio D. Lerner
 */
public class RepositoryTrack extends MutableRepository {

    public RepositoryTrack() {
        trie = new MutableTrieImpl(new TrieImpl());
    }

    public RepositoryTrack(boolean isSecure) {
        trie = new MutableTrieImpl(new TrieImpl(isSecure));
    }

    public RepositoryTrack(Repository aparentRepo) {
        trie = new MutableTrieCache(aparentRepo.getMutableTrie());
        this.parentRepo = aparentRepo;
    }

    public RepositoryTrack(Trie atrie) {
        trie = new MutableTrieCache(new MutableTrieImpl(atrie));
        this.parentRepo =null;
    }

    public RepositoryTrack(Trie atrie,Repository aparentRepo) {
        // If there is no parent then we don't need to track changes
        if (aparentRepo==null)
            trie = new MutableTrieImpl(atrie);
        else
            trie = new MutableTrieCache(new MutableTrieImpl(atrie));
        this.parentRepo = aparentRepo;
    }

}
