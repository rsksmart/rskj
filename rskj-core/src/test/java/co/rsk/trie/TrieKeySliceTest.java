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

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

public class TrieKeySliceTest {

    @Test
    //@Ignore
    // This is a test to perform only once.
    // It checks that, on a 64 bit-system, the size of a KeyTrieSlice object
    // is 24 bytes.
    public void sizeTest() {
        int maxObjects = 1000_000;
        TrieKeySlice[] myObjArray = new TrieKeySlice[maxObjects];
        long beforeUsedMem=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
        for(int i=0;i<myObjArray.length;i++) {
            myObjArray[i] = TrieKeySlice.nullFilledObject();
        }
        long afterUsedMem=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
        long objectsize = (afterUsedMem-beforeUsedMem)/ myObjArray.length;
        System.out.println("Object size: "+(afterUsedMem-beforeUsedMem)*1.0/ myObjArray.length);
        Assert.assertTrue((objectsize>=23) && (objectsize<27));
        myObjArray[0] =null; // avoid garbage collection
    }

    @Test
    public void appendShortOverflows() {
        // While in no place in the RSKj code keys lengths and offset are longer
        // than 592 bits, which fits nicely in a short field, we test here what happens
        // if we try to create such a key.


    }
    @Test
    public void equalsAndCloneTest() {
        TrieKeySlice initialKey = TrieKeySlice.fromExpanded(
                new byte[]{(byte) 1,1,1,1,0,0,0,0, 1,1,1,1}, // 12 bits,
                0,12);
        TrieKeySlice clonedKey = initialKey.clone();
        Assert.assertTrue(initialKey.equals(clonedKey));
        TrieKeySlice modifiedKey = initialKey.appendBit((byte)1);
        Assert.assertFalse(modifiedKey.equals(clonedKey));
        Assert.assertFalse(modifiedKey.equals(initialKey));

        // Now test equals after a slice
        TrieKeySlice slicedKey0 = initialKey.slice(1,5);
        TrieKeySlice lastKey = TrieKeySlice.fromExpanded(
                new byte[]{(byte)  1,1,1,0}, // 4 bits
                0,4);
        Assert.assertTrue(slicedKey0.equals(lastKey));




    }
    @Test
    public void appendTest() {
        // Choose bits not multiple of 8 to cover those cases.
        TrieKeySlice initialKey = TrieKeySlice.fromExpanded(
                new byte[]{(byte) 1,1,1,1,0,0,0,0, 1,1,1,1}, // 12 bits,
                0,12);

        TrieKeySlice appendToKey;
        appendToKey = TrieKeySlice.fromExpanded(
                new byte[]{(byte) 1,0,1,0,1,0,1,0, 1}, // 9 bits,
                0,9);

        TrieKeySlice appendedKey = initialKey.append(appendToKey);

        TrieKeySlice result1 =TrieKeySlice.fromExpanded(
                new byte[]{(byte) 1,1,1,1,0,0,0,0, 1,1,1,1, 1,0,1,0,1,0,1,0, 1},0,21);

        Assert.assertTrue(appendedKey.equals(result1));

        // Now test appendign a single bit
        TrieKeySlice appendedKeyPlusBit0 = appendToKey.appendBit((byte)1);
        TrieKeySlice result2 =TrieKeySlice.fromExpanded(
                new byte[]{(byte) 1,0,1,0,1,0,1,0, 1,1},0,10);

        Assert.assertTrue(appendedKeyPlusBit0.equals(result2));

        // Now append the other bit
        // Now test appendign a single bit
        TrieKeySlice appendedKeyPlusBit1 = appendToKey.appendBit((byte)0);
        TrieKeySlice result3 =TrieKeySlice.fromExpanded(
                new byte[]{(byte) 1,0,1,0,1,0,1,0, 1,0},0,10);

        Assert.assertTrue(appendedKeyPlusBit1.equals(result3));
    }

    @Test
    public void bytesToKey() {
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] { 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00 }),
                TrieKeySlice.fromKey(new byte[]{(byte) 0xaa}).encode()
        );
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] { 0x01, 0x00, 0x01, 0x00, 0x01, 0x00 }),
                TrieKeySlice.fromKey(new byte[]{(byte) 0xaa}).slice(2, 8).encode()
        );
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] { 0x01, 0x00, 0x01, 0x00, 0x01, 0x00 }),
                TrieKeySlice.fromKey(new byte[]{(byte) 0xaa}).slice(0, 6).encode()
        );
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] { 0x00, 0x01, 0x00, 0x01, 0x00 }),
                TrieKeySlice.fromKey(new byte[]{(byte) 0xaa}).slice(1, 6).encode()
        );
    }

    @Test
    public void leftPad() {
        int paddedLength = 8;
        TrieKeySlice initialKey = TrieKeySlice.fromKey(new byte[]{(byte) 0xff});
        TrieKeySlice leftPaddedKey = initialKey.leftPad(paddedLength);

        Assert.assertThat(leftPaddedKey.length(), is(initialKey.length() + paddedLength));
        Assert.assertArrayEquals(
            PathEncoder.encode(new byte[] {
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01
            }),
            leftPaddedKey.encode()
        );
    }
}