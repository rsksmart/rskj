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

import co.rsk.bitcoinj.core.VarInt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class SharedPathSerializer {
    private final TrieKeySlice sharedPath;
    private final int lshared;

    // No need to create this object anymore. Just use the static methods.
    public SharedPathSerializer(TrieKeySlice sharedPath) {
        this.sharedPath = sharedPath;
        this.lshared = this.sharedPath.length();
    }


    public int serializedLength() {
        if (!isPresent()) {
            return 0;
        }

        return lsharedSize() + PathEncoder.calculateEncodedLength(lshared);
    }

    public boolean isPresent() {
        return lshared > 0;
    }

    public void serializeInto(ByteBuffer buffer) {
      serializeInto(this.sharedPath,buffer);
    }

    public static void serializeInto(TrieKeySlice sharedPath,ByteBuffer buffer) {
        if (!isPresent(sharedPath)) {
            return;
        }
        int lshared = sharedPath.length();
        final byte[] encode = sharedPath.encode();
        serializeBytes(buffer, lshared, encode);
    }

    public static void serializeBytes(ByteBuffer buffer, int lshared, byte[] encode) {
        if (1 <= lshared && lshared <= 32) {
            // first byte in [0..31]
            buffer.put((byte) (lshared - 1));
        } else if (160 <= lshared && lshared <= 382) {
            // first byte in [32..254]
            buffer.put((byte) (lshared - 128));
        } else {
            buffer.put((byte) 255);
            buffer.put(new VarInt(lshared).encode());
        }
        buffer.put(encode);
    }

    public static void writeBytes(ByteArrayOutputStream buffer, int lshared, byte[] encode) throws IOException {
        if (1 <= lshared && lshared <= 32) {
            // first byte in [0..31]
            buffer.write((byte) (lshared - 1));
        } else if (160 <= lshared && lshared <= 382) {
            // first byte in [32..254]
            buffer.write((byte) (lshared - 128));
        } else {
            buffer.write((byte) 255);
            buffer.write(new VarInt(lshared).encode());
        }
        buffer.write(encode);
    }

    // Returns the size of the path prefix when path needs encoding.
    // The prefix indicates the length of the shared path in bits.
    // TO DO: Rename to something like getEncodedPathBitlengthSize()
    private static int lsharedSize(TrieKeySlice sharedPath) {
        if (!isPresent(sharedPath)) {
            return 0;
        }
        int lshared = sharedPath.length();
        if (1 <= lshared && lshared <= 32) {
            return 1;
        }

        if (160 <= lshared && lshared <= 382) {
            return 1;
        }

        return 1 + VarInt.sizeOf(lshared);
    }

    private int lsharedSize() {
        return lsharedSize(this.sharedPath);
    }

    public static int getPathBitsLength(ByteBuffer message) {
        int lshared; // this is a bit length

        // upgrade to int so we can compare positive values
        int lsharedFirstByte = Byte.toUnsignedInt(message.get());
        if (0 <= lsharedFirstByte && lsharedFirstByte <= 31) {
            // lshared in [1..32]
            lshared = lsharedFirstByte + 1;
        } else if (32 <= lsharedFirstByte && lsharedFirstByte <= 254) {
            // lshared in [160..382]
            lshared = lsharedFirstByte + 128;
        } else {
            lshared = (int) readVarInt(message).value;
        }
        return lshared;
    }
    public static TrieKeySlice deserialize(ByteBuffer message, boolean sharedPrefixPresent) {
        if (!sharedPrefixPresent) {
            return TrieKeySlice.empty();
        }

        int lshared = getPathBitsLength(message);

        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        byte[] encodedKey = new byte[lencoded];
        message.get(encodedKey);
        return TrieKeySlice.fromEncoded(encodedKey, 0, lshared, lencoded);
    }

    public static byte[] deserializeBytes(ByteBuffer message, boolean sharedPrefixPresent, ByteArrayOutputStream encoder) throws IOException {
        if (!sharedPrefixPresent) {
            return new byte[0];
        }

        int lshared = getPathBitsLength(message);

        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        byte[] encodedKey = new byte[lencoded];
        message.get(encodedKey);
        writeBytes(encoder, lshared, encodedKey);
        return encodedKey;
    }

    private static VarInt readVarInt(ByteBuffer message) {
        // read without touching the buffer position so when we read into bytes it contains the header
        int first = Byte.toUnsignedInt(message.get(message.position()));
        byte[] bytes;
        if (first < 253) {
            bytes = new byte[1];
        } else if (first == 253) {
            bytes = new byte[3];
        } else if (first == 254) {
            bytes = new byte[5];
        } else {
            bytes = new byte[9];
        }

        message.get(bytes);
        return new VarInt(bytes, 0);
    }

    public static boolean isPresent(TrieKeySlice sharedPath) {
        return sharedPath.length() > 0;
    }

    static int getSerializedLength(TrieKeySlice sharedPath) {
        if (!isPresent(sharedPath)) {
            return 0;
        }

        return lsharedSize(sharedPath) + PathEncoder.calculateEncodedLength(sharedPath.length());
    }
}

