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

package co.rsk.trie;

import org.junit.Test;
import org.junit.Assert;

/**
 * Created by ajlopez on 12/01/2017.
 */
public class TrieImplArityTest {
    @Test
    public void getDefaultArity() {
        TrieImpl trie = new TrieImpl();

        Assert.assertEquals(2, trie.getArity());
    }

    @Test
    public void getArityTwo() {
        TrieImpl trie = new TrieImpl(2, false);

        Assert.assertEquals(2, trie.getArity());
    }

    @Test
    public void getArityFour() {
        TrieImpl trie = new TrieImpl(4, false);

        Assert.assertEquals(4, trie.getArity());
    }

    @Test
    public void getAritySixteen() {
        TrieImpl trie = new TrieImpl(16, false);

        Assert.assertEquals(16, trie.getArity());
    }

    @Test
    public void invalidArity() {
        try {
            TrieImpl trie = new TrieImpl(3, false);
            Assert.fail();
        }
        catch (IllegalArgumentException ex) {
            Assert.assertEquals("Invalid arity", ex.getMessage());
        }
    }
}
