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

package co.rsk.trie.delete;

import co.rsk.trie.Trie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Created by martin.medina on 03/04/2017.
 */
class SecureTrieHashTest {

    @Test
    void removeOrNeverInsertShouldBringSameHashWithSecureTrie() {
        Trie trie1 = new Trie()
                .put("roosevalt", "So, first of all, let me assert my firm belief that".getBytes())
                .put("roosevelt", "the only thing we have to fear is... fear itself ".getBytes())
                .put("roosevilt", "42".getBytes())
                .delete("roosevelt");

        Trie trie2 = new Trie()
                .put("roosevalt", "So, first of all, let me assert my firm belief that".getBytes())
                .put("roosevilt", "42".getBytes());

        Assertions.assertTrue(Arrays.equals(trie1.get("roosevalt"), "So, first of all, let me assert my firm belief that".getBytes()));
        Assertions.assertTrue(Arrays.equals(trie1.get("roosevilt"), "42".getBytes()));
        Assertions.assertNull(trie1.get("roosevelt"));
        Assertions.assertEquals(trie1.getHash(), trie2.getHash());
    }
}
