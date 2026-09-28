/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.transaction.widening;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Byte-level RLP framing used to build deliberately non-canonical encodings. Deliberately not
 * {@code org.ethereum.util.RLP}: the mutations here must be able to emit frames the production
 * codec would never produce.
 */
final class RlpFrames {

    private RlpFrames() {
    }

    /** Header of the item starting at {@code offset}. */
    static final class Frame {
        final int start;
        final int payloadStart;
        final int payloadLength;
        final boolean list;

        Frame(int start, int payloadStart, int payloadLength, boolean list) {
            this.start = start;
            this.payloadStart = payloadStart;
            this.payloadLength = payloadLength;
            this.list = list;
        }

        int end() {
            return payloadStart + payloadLength;
        }
    }

    static Frame frameAt(byte[] data, int offset) {
        if (offset < 0 || offset >= data.length) {
            throw new IllegalArgumentException("offset out of range: " + offset);
        }
        int prefix = data[offset] & 0xFF;
        if (prefix <= 0x7F) {
            return new Frame(offset, offset, 1, false);
        }
        if (prefix <= 0xB7) {
            return new Frame(offset, offset + 1, prefix - 0x80, false);
        }
        if (prefix <= 0xBF) {
            int lenOfLen = prefix - 0xB7;
            return new Frame(offset, offset + 1 + lenOfLen, readLength(data, offset + 1, lenOfLen), false);
        }
        if (prefix <= 0xF7) {
            return new Frame(offset, offset + 1, prefix - 0xC0, true);
        }
        int lenOfLen = prefix - 0xF7;
        return new Frame(offset, offset + 1 + lenOfLen, readLength(data, offset + 1, lenOfLen), true);
    }

    private static int readLength(byte[] data, int offset, int lenOfLen) {
        if (offset + lenOfLen > data.length || lenOfLen > 4) {
            throw new IllegalArgumentException("truncated RLP length");
        }
        int length = 0;
        for (int i = 0; i < lenOfLen; i++) {
            length = (length << 8) | (data[offset + i] & 0xFF);
        }
        return length;
    }

    /** Full encodings of the direct children of the list framed at {@code offset}. */
    static List<byte[]> listItems(byte[] data, int offset) {
        Frame outer = frameAt(data, offset);
        if (!outer.list) {
            throw new IllegalArgumentException("not a list at offset " + offset);
        }
        if (outer.end() > data.length) {
            throw new IllegalArgumentException("truncated RLP list");
        }
        List<byte[]> items = new ArrayList<>();
        int cursor = outer.payloadStart;
        while (cursor < outer.end()) {
            Frame item = frameAt(data, cursor);
            if (item.end() > outer.end()) {
                throw new IllegalArgumentException("child overruns its list");
            }
            items.add(slice(data, item.start, item.end()));
            cursor = item.end();
        }
        return items;
    }

    /** Payload carried by a byte-string item encoding; empty for a list item. */
    static byte[] payloadOf(byte[] item) {
        Frame frame = frameAt(item, 0);
        if (frame.list) {
            return new byte[0];
        }
        return slice(item, frame.payloadStart, frame.end());
    }

    static byte[] slice(byte[] data, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(data, from, out, 0, out.length);
        return out;
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    /** Minimal byte-string encoding of {@code payload}. */
    static byte[] encodeItem(byte[] payload) {
        if (payload.length == 1 && (payload[0] & 0xFF) <= 0x7F) {
            return payload.clone();
        }
        return concat(header(payload.length, 0x80, 0xB7), payload);
    }

    /**
     * Byte-string encoding that always uses the long form, so a payload the minimal rules would
     * spell short carries a non-canonical header.
     */
    static byte[] encodeItemLongForm(byte[] payload) {
        return concat(longHeader(payload.length, 0xB7), payload);
    }

    static byte[] encodeList(List<byte[]> items) {
        byte[] payload = concat(items.toArray(new byte[0][]));
        return concat(header(payload.length, 0xC0, 0xF7), payload);
    }

    static byte[] encodeListLongForm(List<byte[]> items) {
        byte[] payload = concat(items.toArray(new byte[0][]));
        return concat(longHeader(payload.length, 0xF7), payload);
    }

    private static byte[] header(int length, int shortBase, int longBase) {
        if (length <= 55) {
            return new byte[]{(byte) (shortBase + length)};
        }
        return longHeader(length, longBase);
    }

    private static byte[] longHeader(int length, int longBase) {
        byte[] lengthBytes = minimalLength(length);
        byte[] out = new byte[1 + lengthBytes.length];
        out[0] = (byte) (longBase + lengthBytes.length);
        System.arraycopy(lengthBytes, 0, out, 1, lengthBytes.length);
        return out;
    }

    private static byte[] minimalLength(int length) {
        if (length == 0) {
            return new byte[]{0};
        }
        int bytes = 0;
        for (int v = length; v != 0; v >>>= 8) {
            bytes++;
        }
        byte[] out = new byte[bytes];
        for (int i = bytes - 1, v = length; i >= 0; i--, v >>>= 8) {
            out[i] = (byte) v;
        }
        return out;
    }
}
