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

package org.ethereum.util;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.RLPException;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.BigIntegers;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.bouncycastle.util.BigIntegers.asUnsignedByteArray;
import static org.ethereum.util.ByteUtil.*;

/**
 * Recursive Length Prefix (RLP) encoding.
 * <p>
 * The purpose of RLP is to encode arbitrarily nested arrays of binary data, and
 * RLP is the main encoding method used to serialize objects in Ethereum. The
 * only purpose of RLP is to encode structure; encoding specific atomic data
 * types (eg. strings, integers, floats) is left up to higher-order protocols; in
 * Ethereum the standard is that integers are represented in big endian binary
 * form. If one wishes to use RLP to encode a dictionary, the two suggested
 * canonical forms are to either use [[k1,v1],[k2,v2]...] with keys in
 * lexicographic order or to use the higher-level Patricia Tree encoding as
 * Ethereum does.
 * <p>
 * The RLP encoding function takes in an item. An item is defined as follows:
 * <p>
 * - A string (ie. byte array) is an item - A list of items is an item
 * <p>
 * For example, an empty string is an item, as is the string containing the word
 * "cat", a list containing any number of strings, as well as more complex data
 * structures like ["cat",["puppy","cow"],"horse",[[]],"pig",[""],"sheep"]. Note
 * that in the context of the rest of this article, "string" will be used as a
 * synonym for "a certain number of bytes of binary data"; no special encodings
 * are used and no knowledge about the content of the strings is implied.
 * <p>
 * See: https://github.com/ethereum/wiki/wiki/%5BEnglish%5D-RLP
 *
 * @author Roman Mandeleil
 * @since 01.04.2014
 */
public class RLP {
    /**
     * Reason for threshold according to Vitalik Buterin:
     * - 56 bytes maximizes the benefit of both options
     * - if we went with 60 then we would have only had 4 slots for long strings
     * so RLP would not have been able to store objects above 4gb
     * - if we went with 48 then RLP would be fine for 2^128 space, but that's way too much
     * - so 56 and 2^64 space seems like the right place to put the cutoff
     * - also, that's where Bitcoin's varint does the cutof
     */
    private static final int SIZE_THRESHOLD = 56;

    /** RLP encoding rules are defined as follows: */

    /*
     * For a single byte whose value is in the [0x00, 0x7f] range, that byte is
     * its own RLP encoding.
     */

    /**
     * [0x80]
     * If a string is 0-55 bytes long, the RLP encoding consists of a single
     * byte with value 0x80 plus the length of the string followed by the
     * string. The range of the first byte is thus [0x80, 0xb7].
     */
    private static final int OFFSET_SHORT_ITEM = 0x80;

    /**
     * [0xb7]
     * If a string is more than 55 bytes long, the RLP encoding consists of a
     * single byte with value 0xb7 plus the length of the length of the string
     * in binary form, followed by the length of the string, followed by the
     * string. For example, a length-1024 string would be encoded as
     * \xb9\x04\x00 followed by the string. The range of the first byte is thus
     * [0xb8, 0xbf].
     */
    private static final int OFFSET_LONG_ITEM = 0xb7;

    /**
     * [0xc0]
     * If the total payload of a list (i.e. the combined length of all its
     * items) is 0-55 bytes long, the RLP encoding consists of a single byte
     * with value 0xc0 plus the length of the list followed by the concatenation
     * of the RLP encodings of the items. The range of the first byte is thus
     * [0xc0, 0xf7].
     */
    private static final int OFFSET_SHORT_LIST = 0xc0;

    /**
     * [0xf7]
     * If the total payload of a list is more than 55 bytes long, the RLP
     * encoding consists of a single byte with value 0xf7 plus the length of the
     * length of the list in binary form, followed by the length of the list,
     * followed by the concatenation of the RLP encodings of the items. The
     * range of the first byte is thus [0xf8, 0xff].
     */
    private static final int OFFSET_LONG_LIST = 0xf7;


    /* ******************************************************
     *                      DECODING                        *
     * ******************************************************/

    public static BigInteger decodeBigInteger(byte[] data, int index) {
        if (data == null) {
            return null;
        }

        RLPElement element = RLP.decodeElement(data, index).getLeft();

        byte[] bytes = element.getRLPData();

        if (bytes == null || bytes.length == 0) {
            return BigInteger.ZERO;
        }

        return BigIntegers.fromUnsignedByteArray(bytes);
    }

    /**
     * Parse wire byte[] message into RLP elements
     *
     * @param msgData - raw RLP data
     * @return rlpList
     * - outcome of recursive RLP structure
     */
    @Nonnull
    public static ArrayList<RLPElement> decodeListElements(@CheckForNull byte[] msgData) {
        ArrayList<RLPElement> elements = new ArrayList<>();

        if (msgData == null) {
            return elements;
        }

        int tlength = msgData.length;
        int position = 0;

        while (position < tlength) {
            Pair<RLPElement, Integer> next = decodeElement(msgData, position);
            elements.add(next.getKey());
            position = next.getValue();
        }

        return elements;
    }

    private static Pair<RLPElement, Integer> decodeElement(byte[] msgData, int position) {
        int b0 = msgData[position] & 0xff;

        if (b0 < OFFSET_SHORT_ITEM) {
            return Pair.of(new RLPItem(new byte[]{ msgData[position] }), position + 1);
        }

        if (b0 == OFFSET_SHORT_ITEM) {
            return Pair.of(new RLPItem(ByteUtil.EMPTY_BYTE_ARRAY), position + 1);
        }

        if (b0 >= OFFSET_SHORT_LIST) {
            return decodeListElement(msgData, position, b0);
        }

        int length;
        int offset;

        if (b0 <= OFFSET_LONG_ITEM) {
            offset = 1;
            length = b0 & 0x7f;
        } else {
            offset = b0 - OFFSET_LONG_ITEM + 1;
            length = bytesToLength(msgData, position + 1, offset - 1);
        }

        if (Long.compareUnsigned(length, Integer.MAX_VALUE) > 0) {
            throw new RLPException("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have");
        }

        if (position + offset + length < 0 || position + offset + length > msgData.length) {
            throw new RLPException("The RLP byte array doesn't have enough space to hold an element with the specified length");
        }

        byte[] decoded = new byte[length];

        System.arraycopy(msgData, position + offset, decoded, 0, length);

        return Pair.of(new RLPItem(decoded), position + offset + length);
    }

    private static Pair<RLPElement, Integer> decodeListElement(byte[] msgData, int position, int b0) {
        int length;
        int offset;

        if (b0 <= OFFSET_LONG_LIST) {
            offset = 1;
            length = b0 - OFFSET_SHORT_LIST + 1;
        } else {
            offset = b0 - OFFSET_LONG_LIST + 1;
            length = offset + bytesToLength(msgData, position + 1, offset - 1);
        }

        if (position + length > msgData.length) {
            throw new RLPException("The RLP byte array doesn't have enough space to hold an element with the specified length");
        }

        byte[] bytes = Arrays.copyOfRange(msgData, position, position + length);
        RLPList list = new RLPList(bytes, offset);

        return Pair.of(list, position + length);
    }

    private static int bytesToLength(byte[] bytes, int position, int size) {
        if (position + size > bytes.length) {
            throw new RLPException("The length of the RLP item length can't possibly fit the data byte array");
        }

        int length = 0;

        for (int k = 0; k < size; k++) {
            length <<= 8;
            length += bytes[position + k] & 0xff;
        }

        if (length < 0) {
            throw new RLPException("The length of the RLP item can't be negative");
        }

        return length;
    }

    /**
     * Parse and verify that the passed data has just one list encoded as RLP
     */
    public static RLPList decodeList(byte[] msgData) {
        if (msgData == null) throw new IllegalArgumentException("The decoded element wasn't a list");
        int b0 = msgData[0] & 0xff;
        if (b0 < OFFSET_SHORT_LIST) throw new RLPException("The decoded element wasn't a list");

        return (RLPList) decodeListElement(msgData, 0, b0).getLeft();
    }

    public static Pair<RLPElement, Integer> decodeFirstElementReading(@CheckForNull byte[] msgData, int position) {
        if (msgData == null) {
            return null;
        }

        return decodeElement(msgData, position);
    }

    public static RLPElement decodeFirstElement(@CheckForNull byte[] msgData, int position) {
        if (msgData == null) {
            return null;
        }

        return decodeElement(msgData, position).getKey();
    }

    @Nonnull
    public static RskAddress parseRskAddress(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return RskAddress.nullAddress();
        } else {
            return new RskAddress(bytes);
        }
    }

    @Nonnull
    public static Coin parseCoin(@Nullable byte[] bytes) {
        if (bytes == null || isAllZeroes(bytes)) {
            return Coin.ZERO;
        } else {
            return new Coin(bytes);
        }
    }

    @Nullable
    public static Coin parseCoinNonNullZero(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        return new Coin(bytes);
    }

    @Nullable
    public static Coin parseSignedCoinNonNullZero(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        return new Coin(new BigInteger(bytes));
    }

    public static Coin parseCoinNullZero(@Nullable byte[] bytes) {
        if (bytes == null) {
            return Coin.ZERO;
        }

        return new Coin(bytes);
    }

    /**
     * @param bytes the difficulty bytes, as expected by {@link BigInteger#BigInteger(byte[])}.
     */
    @Nullable
    public static BlockDifficulty parseBlockDifficulty(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        return new BlockDifficulty(new BigInteger(bytes));
    }

    /* ******************************************************
     *                      ENCODING                        *
     * ******************************************************/

    public static byte[] encodeByte(byte singleByte) {
        if ((singleByte & 0xFF) == 0) {
            return new byte[]{(byte) OFFSET_SHORT_ITEM};
        } else if ((singleByte & 0xFF) <= 0x7F) {
            return new byte[]{singleByte};
        } else {
            return new byte[]{(byte) (OFFSET_SHORT_ITEM + 1), singleByte};
        }
    }

    public static byte[] encodeShort(short singleShort) {
        if ((singleShort & 0xFF) == singleShort) {
            return encodeByte((byte) singleShort);
        } else {
            return new byte[]{(byte) (OFFSET_SHORT_ITEM + 2),
                    (byte) (singleShort >> 8 & 0xFF),
                    (byte) (singleShort >> 0 & 0xFF)};
        }
    }

    public static byte[] encodeInt(int singleInt) {
        if ((singleInt & 0xFF) == singleInt) {
            return encodeByte((byte) singleInt);
        } else if ((singleInt & 0xFFFF) == singleInt) {
            return encodeShort((short) singleInt);
        } else if ((singleInt & 0xFFFFFF) == singleInt) {
            return new byte[]{(byte) (OFFSET_SHORT_ITEM + 3),
                    (byte) (singleInt >>> 16),
                    (byte) (singleInt >>> 8),
                    (byte) singleInt};
        } else {
            return new byte[]{(byte) (OFFSET_SHORT_ITEM + 4),
                    (byte) (singleInt >>> 24),
                    (byte) (singleInt >>> 16),
                    (byte) (singleInt >>> 8),
                    (byte) singleInt};
        }
    }

    public static byte[] encodeString(String srcString) {
        return encodeElement(srcString.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encodeBigInteger(BigInteger srcBigInteger) {
        if (srcBigInteger.equals(BigInteger.ZERO)) {
            return encodeByte((byte) 0);
        } else {
            return encodeElement(asUnsignedByteArray(srcBigInteger));
        }
    }

    public static byte[] encodeRskAddress(RskAddress addr) {
        if (addr == null || RskAddress.nullAddress().equals(addr)) {
            return encodeElement(null);
        }

        return encodeElement(addr.getBytes());
    }

    public static byte[] encodeCoin(@Nullable Coin coin) {
        if (coin == null) {
            return encodeBigInteger(BigInteger.ZERO);
        }

        return encodeBigInteger(coin.asBigInteger());
    }

    public static byte[] encodeCoinNonNullZero(@CheckForNull Coin coin) {
        if (coin == null) {
            return encodeElement(null);
        }

        if (coin.equals(Coin.ZERO)) {
            return new byte[]{0};
        }

        return encodeElement(BigIntegers.asUnsignedByteArray(coin.asBigInteger()));
    }

    public static byte[] encodeSignedCoinNonNullZero(@CheckForNull Coin coin) {
        if (coin == null) {
            return encodeElement(null);
        }

        if (Coin.ZERO.equals(coin)) {
            return new byte[]{0};
        }

        return encodeElement(coin.getBytes());
    }

    public static byte[] encodeCoinNullZero(Coin coin) {
        if (coin.equals(Coin.ZERO)) {
            return encodeByte((byte) 0);
        }

        return encodeCoinNonNullZero(coin);
    }

    public static byte[] encodeBlockDifficulty(BlockDifficulty difficulty) {
        if (difficulty == null) {
            return encodeElement(null);
        }

        return encodeElement(difficulty.getBytes());
    }

    public static byte[] encodeElement(@Nullable byte[] srcData) {
        if (srcData == null || srcData.length == 0) {
            return new byte[]{(byte) OFFSET_SHORT_ITEM};
        } else if (isSingleZero(srcData)) {
            return srcData;
        } else if (srcData.length == 1 && (srcData[0] & 0xFF) < OFFSET_SHORT_ITEM) {
            return srcData;
        } else if (srcData.length < SIZE_THRESHOLD) {
            // length = 8X
            byte length = (byte) (OFFSET_SHORT_ITEM + srcData.length);
            byte[] data = Arrays.copyOf(srcData, srcData.length + 1);
            System.arraycopy(data, 0, data, 1, srcData.length);
            data[0] = length;

            return data;
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = srcData.length;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength = tmpLength >> 8;
            }
            byte[] lenBytes = new byte[byteNum];
            for (int i = 0; i < byteNum; ++i) {
                lenBytes[byteNum - 1 - i] = (byte) ((srcData.length >> (8 * i)) & 0xFF);
            }
            // first byte = F7 + bytes.length
            byte[] data = Arrays.copyOf(srcData, srcData.length + 1 + byteNum);
            System.arraycopy(data, 0, data, 1 + byteNum, srcData.length);
            data[0] = (byte) (OFFSET_LONG_ITEM + byteNum);
            System.arraycopy(lenBytes, 0, data, 1, lenBytes.length);

            return data;
        }
    }

    public static byte[]encodeList(byte[]... elements) {

        if (elements == null) {
            return new byte[]{(byte) OFFSET_SHORT_LIST};
        }

        int totalLength = 0;
        for (byte[] element1 : elements) {
            totalLength += element1.length;
        }

        byte[] data;
        int copyPos;
        if (totalLength < SIZE_THRESHOLD) {

            data = new byte[1 + totalLength];
            data[0] = (byte) (OFFSET_SHORT_LIST + totalLength);
            copyPos = 1;
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = totalLength;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength = tmpLength >> 8;
            }
            tmpLength = totalLength;
            byte[] lenBytes = new byte[byteNum];
            for (int i = 0; i < byteNum; ++i) {
                lenBytes[byteNum - 1 - i] = (byte) ((tmpLength >> (8 * i)) & 0xFF);
            }
            // first byte = F7 + bytes.length
            data = new byte[1 + lenBytes.length + totalLength];
            data[0] = (byte) (OFFSET_LONG_LIST + byteNum);
            System.arraycopy(lenBytes, 0, data, 1, lenBytes.length);

            copyPos = lenBytes.length + 1;
        }
        for (byte[] element : elements) {
            System.arraycopy(element, 0, data, copyPos, element.length);
            copyPos += element.length;
        }
        return data;
    }
}