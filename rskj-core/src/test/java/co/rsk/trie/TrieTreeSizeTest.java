/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.trie;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

public class TrieTreeSizeTest {
    @Test
    public void emptyChildrenSize() {
        Trie trie = new Trie();
        long emptyChildrenSize = trie.getChildrenSize().value;
        MatcherAssert.assertThat(emptyChildrenSize, is(0L));
    }

    @Test
    public void childrenSizeShortValue() {
        Trie trie = new Trie()
                .put(new byte[]{0x00}, new byte[]{0x01})
                .put(new byte[]{0x01}, new byte[32]);
        MatcherAssert.assertThat(trie.getChildrenSize().value, is(35L));
    }

    @Test
    public void childrenSizeLongValue() {
        Trie trie = new Trie()
                .put(new byte[]{0x00}, new byte[]{0x01})
                .put(new byte[]{0x01}, new byte[33]);
        MatcherAssert.assertThat(trie.getChildrenSize().value, is(71L));
    }
}
