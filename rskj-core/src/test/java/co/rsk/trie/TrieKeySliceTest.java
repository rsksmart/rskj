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

import static org.hamcrest.Matchers.is;

import org.junit.Assert;
import org.junit.Test;

public class TrieKeySliceTest {
    @Test
    public void bytesToKey() {
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] {0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00}),
                TrieKeySlice.fromKey(new byte[] {(byte) 0xaa}).encode());
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] {0x01, 0x00, 0x01, 0x00, 0x01, 0x00}),
                TrieKeySlice.fromKey(new byte[] {(byte) 0xaa}).slice(2, 8).encode());
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] {0x01, 0x00, 0x01, 0x00, 0x01, 0x00}),
                TrieKeySlice.fromKey(new byte[] {(byte) 0xaa}).slice(0, 6).encode());
        Assert.assertArrayEquals(
                PathEncoder.encode(new byte[] {0x00, 0x01, 0x00, 0x01, 0x00}),
                TrieKeySlice.fromKey(new byte[] {(byte) 0xaa}).slice(1, 6).encode());
    }

    @Test
    public void leftPad() {
        int paddedLength = 8;
        TrieKeySlice initialKey = TrieKeySlice.fromKey(new byte[] {(byte) 0xff});
        TrieKeySlice leftPaddedKey = initialKey.leftPad(paddedLength);

        Assert.assertThat(leftPaddedKey.length(), is(initialKey.length() + paddedLength));
        Assert.assertArrayEquals(
                PathEncoder.encode(
                        new byte[] {
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01
                        }),
                leftPaddedKey.encode());
    }
}
