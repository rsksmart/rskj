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
import co.rsk.trie.TrieImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.keccak256;

/**
 * Created by martin.medina on 11/01/2017.
 */
public class TrieImplHashTest {

    @Test
    public void removeOrNeverInsertShouldBringSameHash() {
        Trie trie1 = new TrieImpl().put("roosevalt", "So, first of all, let me assert my firm belief that".getBytes())
                .put("roosevelt", "the only thing we have to fear is... fear itself ".getBytes())
                .put("roosevilt", "42".getBytes())
                .delete("roosevelt");

        Trie trie2 = new TrieImpl().put("roosevalt", "So, first of all, let me assert my firm belief that".getBytes())
                .put("roosevilt", "42".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("roosevalt"), "So, first of all, let me assert my firm belief that".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevilt"), "42".getBytes()));
        Assert.assertNull(trie1.get("roosevelt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithNoSiblingsAndOnlyOneSonShouldBringSameHashBaseCase() {
        Trie trie1 = new TrieImpl().put("roose", "42".getBytes())
                .put("roosevalt", "4243".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl().put("roose", "42".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("roose"), "42".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevalt"), "424344".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithNoSiblingsAndOnlyOneSonShouldBringSameHashRecursionCase() {
        Trie trie1 = new TrieImpl()
                .put("ro", "4".getBytes())
                .put("roose", "42".getBytes())
                .put("roosevalt", "4243".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl()
                .put("ro", "4".getBytes())
                .put("roose", "42".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("ro"), "4".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roose"), "42".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevalt"), "424344".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithNoSiblingsAndOnlyOneSonWithSonsShouldBringSameHashBaseCase() {
        Trie trie1 = new TrieImpl().put("roose", "42".getBytes())
                .put("roosevalt", "4243".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes())
                .put("roosevaltroosevaltroosevaltroosevalt", "42434445".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl().put("roose", "42".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes())
                .put("roosevaltroosevaltroosevaltroosevalt", "42434445".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("roose"), "42".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevalt"), "424344".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevaltroosevaltroosevalt"), "42434445".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithNoSiblingsAndOnlyOneSonWithSonsShouldBringSameHashRecursionCase() {
        Trie trie1 = new TrieImpl()
                .put("ro", "4".getBytes())
                .put("roose", "42".getBytes())
                .put("roosevalt", "4243".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes())
                .put("roosevaltroosevaltroosevaltroosevalt", "42434445".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl()
                .put("ro", "4".getBytes())
                .put("roose", "42".getBytes())
                .put("roosevaltroosevalt", "424344".getBytes())
                .put("roosevaltroosevaltroosevaltroosevalt", "42434445".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("ro"), "4".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roose"), "42".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevalt"), "424344".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevaltroosevaltroosevalt"), "42434445".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithNoSiblingsAndTwoSonsShouldBringSameHashBaseCase() {
        Trie trie1 = new TrieImpl().put("roose", "42".getBytes())
                .put("roosevalt", "4243".getBytes())
                .put("roosevalt0oosevalt", "424344".getBytes())
                .put("roosevalt1oosevalt", "42434445".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl().put("roose", "42".getBytes())
                .put("roosevalt0oosevalt", "424344".getBytes())
                .put("roosevalt1oosevalt", "42434445".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("roose"), "42".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevalt0oosevalt"), "424344".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevalt1oosevalt"), "42434445".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithNoSiblingsAndTwoSonsShouldBringSameHashRecursionCase() {
        Trie trie1 = new TrieImpl()
                .put("ro", "4".getBytes())
                .put("roose", "42".getBytes())
                .put("roosevalt", "4243".getBytes())
                .put("roosevalt0oosevalt", "424344".getBytes())
                .put("roosevalt1oosevalt", "42434445".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl()
                .put("ro", "4".getBytes())
                .put("roose", "42".getBytes())
                .put("roosevalt0oosevalt", "424344".getBytes())
                .put("roosevalt1oosevalt", "42434445".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("ro"), "4".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roose"), "42".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevalt0oosevalt"), "424344".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevalt1oosevalt"), "42434445".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }

    @Test
    public void sonWithSiblingAndOnlyOneGrandsonShouldBringSameHashBaseCase() {
        Trie trie1 = new TrieImpl()
                .put("roosevalt", "4243".getBytes())
                .put("rooseval_", "424344".getBytes())
                .put("roosevaltroosevalt", "42434445".getBytes())
                .delete("roosevalt");

        Trie trie2 = new TrieImpl()
                .put("rooseval_", "424344".getBytes())
                .put("roosevaltroosevalt", "42434445".getBytes());

        Assert.assertTrue(Arrays.equals(trie1.get("rooseval_"), "424344".getBytes()));
        Assert.assertTrue(Arrays.equals(trie1.get("roosevaltroosevalt"), "42434445".getBytes()));
        Assert.assertNull(trie1.get("roosevalt"));
        Assert.assertTrue(Arrays.equals(trie1.getHash(), trie2.getHash()));
    }
}
