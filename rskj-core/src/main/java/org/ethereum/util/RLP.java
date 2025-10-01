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
import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.types.bytes.BytesSlice;
import co.rsk.util.RLPException;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.db.ByteArrayWrapper;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.bouncycastle.util.Arrays.concatenate;
import static org.bouncycastle.util.BigIntegers.asUnsignedByteArray;
import static org.ethereum.util.ByteUtil.isAllZeroes;
import static org.ethereum.util.ByteUtil.intToBytesNoLeadZeroes;
import static org.ethereum.util.ByteUtil.isSingleZero;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;


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
    private static final int EMPTY_MARK = 128;
    private static final int TINY_SIZE = 55;

    /**
     * Allow for content up to size of 2^64 bytes *
     */
    private static final double MAX_ITEM_LENGTH = Math.pow(256, 8);

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
    @VisibleForTesting
    static final int OFFSET_SHORT_ITEM = 0x80;

    /**
     * [0xb7]
     * If a string is more than 55 bytes long, the RLP encoding consists of a
     * single byte with value 0xb7 plus the length of the length of the string
     * in binary form, followed by the length of the string, followed by the
     * string. For example, a length-1024 string would be encoded as
     * \xb9\x04\x00 followed by the string. The range of the first byte is thus
     * [0xb8, 0xbf].
     */
    @VisibleForTesting
    static final int OFFSET_LONG_ITEM = 0xb7;

    /**
     * [0xc0]
     * If the total payload of a list (i.e. the combined length of all its
     * items) is 0-55 bytes long, the RLP encoding consists of a single byte
     * with value 0xc0 plus the length of the list followed by the concatenation
     * of the RLP encodings of the items. The range of the first byte is thus
     * [0xc0, 0xf7].
     */
    @VisibleForTesting
    static final int OFFSET_SHORT_LIST = 0xc0;

    /**
     * [0xf7]
     * If the total payload of a list is more than 55 bytes long, the RLP
     * encoding consists of a single byte with value 0xf7 plus the length of the
     * length of the list in binary form, followed by the length of the list,
     * followed by the concatenation of the RLP encodings of the items. The
     * range of the first byte is thus [0xf8, 0xff].
     */
    @VisibleForTesting
    static final int OFFSET_LONG_LIST = 0xf7;


    /* ******************************************************
     *                      DECODING                        *
     * ******************************************************/

    public static int decodeInt(byte[] data, int index) {
        // NOTE: there are two ways zero can be encoded - 0x00 and OFFSET_SHORT_ITEM

        if (index < 0 || index >= data.length) {
            throw new RLPException("Index out of bounds");
        }

        int firstByte = data[index] & 0xFF;

        if (firstByte < OFFSET_SHORT_ITEM) {
            return data[index];
        }

        if (firstByte < OFFSET_LONG_ITEM) {
            int value = 0;

            byte length = (byte) (data[index] - OFFSET_SHORT_ITEM);
            if(length  >= (data.length - index)) {
                throw new RLPException("RLP wrong encoding");
            }
            byte pow = (byte) (length - 1);
            for (int i = 1; i <= length; ++i) {
                value += (data[index + i] & 0xFF) << (8 * pow);
                pow--;
            }
            return value;
        }
        throw new RLPException("wrong decode attempt");
    }

    public static BigInteger decodeBigInteger(byte[] data, int index) {
        RLPElement element = RLP.decodeFirstElement(data, index);

        if (element == null) {
            return null;
        }

        byte[] bytes = element.getRLPData();

        if (bytes == null || bytes.length == 0) {
            return BigInteger.ZERO;
        }

        return BigIntegers.fromUnsignedByteArray(bytes);
    }

    private static void assertLengthOfLength(byte[] payload, byte lengthOfLength, int pos) {
        if (lengthOfLength < 0 || lengthOfLength + pos >= payload.length) {
            throw new RLPException("lengthOfLength cannot be out of the payload length");
        }
    }

    public static int getNextElementIndex(byte[] payload, int pos) {

        if (pos < 0 || pos >= payload.length) {
            throw new RLPException("pos out of payload length range");
        }

        int nextElementIndex;
        final var firstByte = (payload[pos] & 0xFF);

        if (firstByte >= OFFSET_LONG_LIST) {
            byte lengthOfLength = (byte) (payload[pos] - OFFSET_LONG_LIST);

            assertLengthOfLength(payload, lengthOfLength, pos);

            int length = calcLength(lengthOfLength, payload, pos);
            nextElementIndex = pos + lengthOfLength + length + 1;
        } else if (firstByte >= OFFSET_SHORT_LIST) {

            byte length = (byte) (firstByte - OFFSET_SHORT_LIST);
            nextElementIndex = pos + 1 + length;
        } else if (firstByte >= OFFSET_LONG_ITEM) {

            byte lengthOfLength = (byte) (payload[pos] - OFFSET_LONG_ITEM);

            assertLengthOfLength(payload, lengthOfLength, pos);

            int length = calcLength(lengthOfLength, payload, pos);
            nextElementIndex = pos + lengthOfLength + length + 1;
        } else if (firstByte > OFFSET_SHORT_ITEM) {

            byte length = (byte) (firstByte - OFFSET_SHORT_ITEM);
            nextElementIndex = pos + 1 + length;
        } else if (firstByte == OFFSET_SHORT_ITEM) {
            nextElementIndex = pos + 1;
        } else {
            nextElementIndex = pos + 1;
        }

        if (pos + 1 > nextElementIndex || nextElementIndex >= payload.length) {
            throw new RLPException("Next element index out of next pos and payload length range");
        }

        return nextElementIndex;
    }

    static int calcLength(int lengthOfLength, byte[] msgData, int pos) {
        byte pow = (byte) (lengthOfLength - 1);
        int length = 0;
        for (int i = 1; i <= lengthOfLength; ++i) {
            length += (msgData[pos + i] & 0xFF) << (8 * pow);
            pow--;
        }
        return length;
    }

    @Nonnull
    public static ArrayList<RLPElement> decode2(@CheckForNull byte[] msgData) {
        return decode2(Bytes.of(msgData));
    }

    /**
     * Parse wire byte[] message into RLP elements
     *
     * @param msgData - raw RLP data
     * @return rlpList
     * - outcome of recursive RLP structure
     */
    @Nonnull
    public static ArrayList<RLPElement> decode2(@CheckForNull BytesSlice msgData) {
        ArrayList<RLPElement> elements = new ArrayList<>();

        if (msgData == null) {
            return elements;
        }

        int tlength = msgData.length();
        int position = 0;

        while (position < tlength) {
            Pair<RLPElement, Integer> next = decodeElement(msgData, position);
            elements.add(next.getKey());
            position = next.getValue();
        }

        return elements;
    }

    public static RLPElement decodeFirstElement(@CheckForNull byte[] msgData, int position) {
        if (msgData == null) {
            return null;
        }

        return decodeElement(Bytes.of(msgData), position).getKey();
    }

    private static Pair<RLPElement, Integer> decodeElement(BytesSlice msgData, int position) {
        int b0 = msgData.byteAt(position) & 0xff;

        if (b0 >= 192) {
            int length;
            int offset;

            if (b0 <= 192 + TINY_SIZE) {
                length = b0 - 192 + 1;
                offset = 1;
            } else {
                int nbytes = b0 - 247;
                offset = 1 + nbytes;
                length = safeAdd(offset, bytesToLength(msgData, position + 1, nbytes));
            }

            int endingIndex = safeAdd(length, position);

            if (endingIndex > msgData.length()) {
                throw new RLPException("The RLP byte array doesn't have enough space to hold an element with the specified length");
            }

            RLPList list = new RLPList(msgData.slice(position, endingIndex), offset);

            return Pair.of(list, endingIndex);
        }

        if (b0 == EMPTY_MARK) {
            return Pair.of(new RLPItem(Bytes.of(EMPTY_BYTE_ARRAY)), position + 1);
        }

        if (b0 < EMPTY_MARK) {
            byte[] data = new byte[1];
            data[0] = msgData.byteAt(position);
            return Pair.of(new RLPItem(Bytes.of(data)), position + 1);
        }

        int length;
        int offset;

        if (b0 > (EMPTY_MARK + TINY_SIZE)) {
            offset = b0 - (EMPTY_MARK + TINY_SIZE) + 1;
            length = bytesToLength(msgData, position + 1, offset - 1);
        } else {
            length = b0 & 0x7f;
            offset = 1;
        }

        if (Long.compareUnsigned(length, Integer.MAX_VALUE) > 0) {
            throw new RLPException("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have");
        }

        int endingIndex = position + offset + length;
        if (endingIndex < 0 || endingIndex > msgData.length()) {
            throw new RLPException("The RLP byte array doesn't have enough space to hold an element with the specified length");
        }

        int from = position + offset;
        int to = from + length;
        return Pair.of(new RLPItem(msgData.slice(from, to)), to);
    }

    private static int safeAdd(int a, int b) {
        try{
          return Math.addExact(a, b);
        }catch (ArithmeticException ex){
          throw new RLPException("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have. " + ex.getMessage() );
        }
    }

    private static int bytesToLength(BytesSlice bytes, int position, int size) {
        if (position + size > bytes.length()) {
            throw new RLPException("The length of the RLP item length can't possibly fit the data byte array");
        }

        int length = 0;

        for (int k = 0; k < size; k++) {
            length <<= 8;
            length += bytes.byteAt(position + k) & 0xff;
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
        List<RLPElement> decoded = RLP.decode2(msgData);
        if (decoded.size() != 1) {
            throw new IllegalArgumentException(String.format("Expected one RLP item but got %d", decoded.size()));
        }

        RLPElement element = decoded.get(0);
        if (!(element instanceof RLPList)) {
            throw new IllegalArgumentException("The decoded element wasn't a list");
        }

        return (RLPList) element;
    }

    @Nullable
    public static RLPElement decode2OneItem(@CheckForNull byte[] msgData, int startPos) {
        if (msgData == null) {
            return null;
        }

        return RLP.decodeFirstElement(msgData, startPos);
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

    /**
     * Turn Object into its RLP encoded equivalent of a byte-array
     * Support for String, Integer, BigInteger and Lists of any of these types.
     *
     * @param input as object or List of objects
     * @return byte[] RLP encoded
     */
    public static byte[] encode(Object input) {
        Value val = new Value(input);
        if (val.isList()) {
            List<Object> inputArray = val.asList();
            if (inputArray.isEmpty()) {
                return encodeLength(inputArray.size(), OFFSET_SHORT_LIST);
            }
            byte[] output = EMPTY_BYTE_ARRAY;
            for (Object object : inputArray) {
                output = concatenate(output, encode(object));
            }
            byte[] prefix = encodeLength(output.length, OFFSET_SHORT_LIST);
            return concatenate(prefix, output);
        } else {
            byte[] inputAsBytes = toBytes(input);
            if (inputAsBytes.length == 1 && (inputAsBytes[0] & 0xff) < 0x80) {
                return inputAsBytes;
            } else {
                byte[] firstByte = encodeLength(inputAsBytes.length, OFFSET_SHORT_ITEM);
                return concatenate(firstByte, inputAsBytes);
            }
        }
    }

    /**
     * Integer limitation goes up to 2^31-1 so length can never be bigger than MAX_ITEM_LENGTH
     */
    public static byte[] encodeLength(int length, int offset) {
        if (length < SIZE_THRESHOLD) {
            byte firstByte = (byte) (length + offset);
            return new byte[]{firstByte};
        } else if (length < MAX_ITEM_LENGTH) {
            byte[] binaryLength;
            if (length > 0xFF) {
                binaryLength = intToBytesNoLeadZeroes(length);
            } else {
                binaryLength = new byte[]{(byte) length};
            }
            byte firstByte = (byte) (binaryLength.length + offset + SIZE_THRESHOLD - 1);
            return concatenate(new byte[]{firstByte}, binaryLength);
        } else {
            throw new RuntimeException("Input too long");
        }
    }

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

    public static byte[] encodeListHeader(int size) {

        if (size == 0) {
            return new byte[]{(byte) OFFSET_SHORT_LIST};
        }

        int totalLength = size;

        byte[] header;
        if (totalLength < SIZE_THRESHOLD) {

            header = new byte[1];
            header[0] = (byte) (OFFSET_SHORT_LIST + totalLength);
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
            header = new byte[1 + lenBytes.length];
            header[0] = (byte) (OFFSET_LONG_LIST + byteNum);
            System.arraycopy(lenBytes, 0, header, 1, lenBytes.length);

        }

        return header;
    }

    public static byte[] encodeSet(Set<ByteArrayWrapper> data) {

        int dataLength = 0;
        Set<byte[]> encodedElements = new HashSet<>();
        for (ByteArrayWrapper element : data) {

            byte[] encodedElement = RLP.encodeElement(element.getData());
            dataLength += encodedElement.length;
            encodedElements.add(encodedElement);
        }

        byte[] listHeader = encodeListHeader(dataLength);

        byte[] output = new byte[listHeader.length + dataLength];

        System.arraycopy(listHeader, 0, output, 0, listHeader.length);

        int cummStart = listHeader.length;
        for (byte[] element : encodedElements) {
            System.arraycopy(element, 0, output, cummStart, element.length);
            cummStart += element.length;
        }

        return output;
    }

    public static byte[] encodeList(byte[]... elements) {

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

    /*
     *  Utility function to convert Objects into byte arrays
     */
    private static byte[] toBytes(Object input) {
        if (input instanceof byte[]) {
            return (byte[]) input;
        } else if (input instanceof String) {
            String inputString = (String) input;
            return inputString.getBytes(StandardCharsets.UTF_8);
        } else if (input instanceof Long) {
            Long inputLong = (Long) input;
            return (inputLong == 0) ? EMPTY_BYTE_ARRAY : asUnsignedByteArray(BigInteger.valueOf(inputLong));
        } else if (input instanceof Integer) {
            Integer inputInt = (Integer) input;
            return (inputInt == 0) ? EMPTY_BYTE_ARRAY : asUnsignedByteArray(BigInteger.valueOf(inputInt));
        } else if (input instanceof BigInteger) {
            BigInteger inputBigInt = (BigInteger) input;
            return (inputBigInt.equals(BigInteger.ZERO)) ? EMPTY_BYTE_ARRAY : asUnsignedByteArray(inputBigInt);
        } else if (input instanceof Value) {
            Value val = (Value) input;
            return toBytes(val.asObj());
        }
        throw new RuntimeException("Unsupported type: Only accepting String, Integer and BigInteger for now");
    }

    /**
     * An encoded empty list
     */
    public static byte[] encodedEmptyList() {
        return new byte[] {(byte) OFFSET_SHORT_LIST};
    }

    /**
     * An encoded empty byte array
     */
    public static byte[] encodedEmptyByteArray() {
        return new byte[] {(byte) OFFSET_SHORT_ITEM};
    }
}