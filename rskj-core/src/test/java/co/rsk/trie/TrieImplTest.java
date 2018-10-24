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

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Random;

/**
 * Created by ajlopez on 22/08/2016.
 */
public class TrieImplTest {
    @Test
    public void bytesToKey() {
        Assert.assertArrayEquals(new byte[] { 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00 }, TrieImpl.bytesToKey(new byte[] { (byte)0xaa }));
    }

    public static byte[] randomKey(Random random ){

        byte[] randomKey = new byte[random.nextInt(20)+1];
        random.nextBytes(randomKey);
        return randomKey;
    }

    @Ignore
    @Test
    public void trieConverter() {
        int max = 100;
        Trie trie = new TrieImpl();
        Random rand = new Random(0); // seed with zero

        for (int k = 0; k < max; k++)
            trie = trie.put(randomKey(rand), TrieImplValueTest.makeValue(rand.nextInt(64)+1));

        //Assert.assertEquals(trie.getHash().toHexString(),"");
        System.out.println(trie.getHash().toHexString());
    }
}

