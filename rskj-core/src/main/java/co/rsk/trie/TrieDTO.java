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
import co.rsk.core.types.ints.Uint24;
import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

public class TrieDTO {

    private static final Logger logger = LoggerFactory.getLogger(TrieDTO.class);

    // ----- FLAGS:
    private boolean hasLongVal;

    private boolean sharedPrefixPresent;
    private boolean leftNodePresent;
    private boolean rightNodePresent;

    private boolean leftNodeEmbedded;
    private boolean rightNodeEmbedded;
    // -----

    // left and right are used when embedded nodes
    private byte[] left;
    private byte[] right;

    // hashes are used when not embedded nodes
    private byte[] leftHash;
    private byte[] rightHash;

    // encoded has the essential data to rebuild the trie.
    private byte[] encoded;

    // source has the source data retrieved from the DB.
    private byte[] source;

    //
    private byte[] value;

    // childrenSize is the size its children (in bytes)
    private VarInt childrenSize;
    private byte[] path;
    private Integer pathLength;
    private byte flags;
    private TrieDTO leftNode;
    private TrieDTO rightNode;
    private byte[] hash;

    public TrieDTO() {
    }

    public static TrieDTO decodeFromMessage(byte[] src, TrieStore ds) {
        return decodeFromMessage(src, ds, false, null);
    }

    public static TrieDTO decodeFromMessage(byte[] src, TrieStore ds, boolean preloadChildren, byte[] hash) {
        TrieDTO result = new TrieDTO();
        try {
            ByteArrayOutputStream encoder = new ByteArrayOutputStream();
            ByteBuffer srcWrap = ByteBuffer.wrap(src);
            byte flags = srcWrap.get();
            //1.flags
            encoder.write(flags);
            result.flags = flags;
            result.hasLongVal = (flags & 0b00100000) == 0b00100000;
            result.sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
            result.leftNodePresent = (flags & 0b00001000) == 0b00001000;
            result.rightNodePresent = (flags & 0b00000100) == 0b00000100;
            result.leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
            result.rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;

            //(*optional) 2.sharedPath - if sharedPrefixPresent
            final Pair<Integer, byte[]> pathTuple = SharedPathSerializer.deserializeEncoded(srcWrap, result.sharedPrefixPresent, encoder);
            result.pathLength = pathTuple != null ? pathTuple.getKey() : null;
            result.path = pathTuple != null ? pathTuple.getValue() : null;

            //(*optional) 3.left - if present & !embedded => hash

            if (result.leftNodePresent && result.leftNodeEmbedded) {
                result.leftNode = TrieDTO.decodeFromMessage(readChildEmbedded(srcWrap, decodeUint8(), Uint8.BYTES), ds, false, hash);
                result.left = result.leftNode.getEncoded();
                result.leftHash = null;
                encoder.write(encodeUint24(result.left.length));
                encoder.write(result.left);
            } else if (result.leftNodePresent) {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                srcWrap.get(valueHash);
                result.left = preloadChildren ? valueHash : null;
                result.leftHash = valueHash;
            }

            //(*optional) 3.right - if present & !embedded => hash
            if (result.rightNodePresent && result.rightNodeEmbedded) {
                result.rightNode = TrieDTO.decodeFromMessage(readChildEmbedded(srcWrap, decodeUint8(), Uint8.BYTES), ds, false, hash);
                result.right = result.rightNode.getEncoded();
                result.rightHash = null;
                encoder.write(encodeUint24(result.right.length));
                encoder.write(result.right);
            } else if (result.rightNodePresent) {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                srcWrap.get(valueHash);
                result.right = preloadChildren ? valueHash : null;
                result.rightHash = valueHash;
            }

            result.childrenSize = new VarInt(0);
            if (result.leftNodePresent || result.rightNodePresent) {
                //(*optional) 4.childrenSize - if any children present
                result.childrenSize = readVarInt(srcWrap, encoder);
            }

            if (result.hasLongVal) {
                byte[] valueHashBytes = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                srcWrap.get(valueHashBytes);
                byte[] lvalueBytes = new byte[Uint24.BYTES];
                srcWrap.get(lvalueBytes);
                byte[] value = ds.retrieveValue(valueHashBytes);
                encoder.write(value);
                result.value = value;
            } else {
                int remaining = srcWrap.remaining();
                byte[] value = new byte[remaining];
                srcWrap.get(value);
                //(*optional) 5.value - if !longValue => value
                encoder.write(value);
                result.value = value;
            }

            if (srcWrap.hasRemaining()) {
                throw new IllegalArgumentException("The srcWrap had more data than expected");
            }
            result.hash = hash;
            result.encoded = encoder.toByteArray();
            result.source = ArrayUtils.clone(src);
        } catch (IOException e) {
            throw new RuntimeException("Error while decoding.", e);
        }
        return result;
    }

    public static TrieDTO decodeFromSync(byte[] src) {
        TrieDTO result = new TrieDTO();
        try {
            ByteBuffer srcWrap = ByteBuffer.wrap(src);
            result.flags = srcWrap.get();
            result.hasLongVal = (result.flags & 0b00100000) == 0b00100000;
            result.sharedPrefixPresent = (result.flags & 0b00010000) == 0b00010000;
            result.leftNodePresent = (result.flags & 0b00001000) == 0b00001000;
            result.rightNodePresent = (result.flags & 0b00000100) == 0b00000100;
            result.leftNodeEmbedded = (result.flags & 0b00000010) == 0b00000010;
            result.rightNodeEmbedded = (result.flags & 0b00000001) == 0b00000001;

            final Pair<Integer, byte[]> pathTuple = SharedPathSerializer.deserializeEncoded(srcWrap, result.sharedPrefixPresent, null);
            result.pathLength = pathTuple != null ? pathTuple.getKey() : null;
            result.path = pathTuple != null ? pathTuple.getValue() : null;

            result.leftHash = null;
            if (result.leftNodeEmbedded) {
                final byte[] leftBytes = readChildEmbedded(srcWrap, decodeUint24(), Uint24.BYTES);
                result.leftNode = TrieDTO.decodeFromSync(leftBytes);
                result.left = result.leftNode.toMessage();
            }

            result.rightHash = null;
            if (result.rightNodeEmbedded) {
                final byte[] rightBytes = readChildEmbedded(srcWrap, decodeUint24(), Uint24.BYTES);
                result.rightNode = TrieDTO.decodeFromSync(rightBytes);
                result.right = result.rightNode.toMessage();
            }

            result.childrenSize = new VarInt(0);
            if (result.leftNodePresent || result.rightNodePresent) {
                result.childrenSize = readVarInt(srcWrap, null);
            }

            int remaining = srcWrap.remaining();
            byte[] value = new byte[remaining];
            srcWrap.get(value);
            result.value = value;
            //result.source = ArrayUtils.clone(src);
        } catch (IOException e) {
            logger.trace("Error while decoding: {}", e.getMessage());
        }
        return result;
    }

    private static byte[] readChildEmbedded(ByteBuffer srcWrap, Function<byte[], byte[]> decode, int uintBytes) throws IOException {
        byte[] lengthBytes = new byte[uintBytes];
        srcWrap.get(lengthBytes);
        byte[] serializedNode = decode.apply(lengthBytes);
        srcWrap.get(serializedNode);
        return serializedNode;
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107
     * <p>
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * It shouldn't be called from outside. It's still public for NodeReference call
     */
    public VarInt getChildrenSize() {
        if (childrenSize == null) {
            childrenSize = new VarInt(0);
        }
        return childrenSize;
    }

    public long getSize() {
        long externalValueLength = this.hasLongVal ? this.value.length : 0L;
        final long left = getLeftSize();
        final long right = getRightSize();
        return externalValueLength + this.source.length + left + right;
    }

    public long getLeftSize() {
        return leftNodeEmbedded ? this.leftNode.getTotalSize() : 0L;
    }

    public long getRightSize() {
        return rightNodeEmbedded ? this.rightNode.getTotalSize() : 0L;
    }

    public long getTotalSize() {
        long externalValueLength = this.hasLongVal ? this.value.length : 0L;
        long nodeSize = externalValueLength + this.source.length;
        return this.getChildrenSize().value + nodeSize;
    }

    private static VarInt readVarInt(ByteBuffer message, ByteArrayOutputStream encoder) throws IOException {
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
        if (encoder != null) {
            encoder.write(bytes);
        }
        return new VarInt(bytes, 0);
    }

    public byte[] getRightHash() {
        return this.rightNodePresent && !this.rightNodeEmbedded ? rightHash : null;
    }

    public byte[] getLeftHash() {
        return this.leftNodePresent && !this.leftNodeEmbedded ? leftHash : null;
    }

    public byte[] getEncoded() {
        return encoded;
    }

    public byte[] getSource() {
        return this.source;
    }

    public byte[] getValue() {
        return value;
    }

    public boolean isTerminal() {
        return (!this.leftNodePresent && !this.rightNodePresent) ||
                !((this.leftNodePresent && !this.leftNodeEmbedded) ||
                        (this.rightNodePresent && !this.rightNodeEmbedded));
    }

    public byte[] getLeft() {
        return left;
    }

    public byte[] getRight() {
        return right;
    }

    public void setLeft(byte[] left) {
        this.left = left;
    }

    public void setRight(byte[] right) {
        this.right = right;
    }

    public TrieDTO getLeftNode() {
        return leftNode;
    }

    public TrieDTO getRightNode() {
        return rightNode;
    }
    public void setLeftHash(byte[] hash) {
        this.leftHash = hash;
    }

    public void setRightHash(byte[] hash) {
        this.rightHash = hash;
    }
    public boolean isLeftNodePresent() {
        return leftNodePresent;
    }

    public boolean isRightNodePresent() {
        return rightNodePresent;
    }

    public boolean isLeftNodeEmbedded() {
        return leftNodeEmbedded;
    }

    public boolean isRightNodeEmbedded() {
        return rightNodeEmbedded;
    }

    public boolean isSharedPrefixPresent() {
        return sharedPrefixPresent;
    }
    public byte[] getPath() {
        return this.path;
    }

    public Integer getPathLength() {
        return pathLength;
    }

    public boolean isHasLongVal() {
        return hasLongVal;
    }
    @Override
    public String toString() {
        return "Node{" + HexUtils.toJsonHex(this.path) + "}:" + this.childrenSize.value;
    }

    public String toDescription() {
        return "Node{" + HexUtils.toJsonHex(this.path) + "}:\n" +
                "{isTerminal()=" + this.isTerminal() + "},\n" +
                "{getSize()=" + this.getSize() + "},\n" +
                "{getTotalSize()=" + this.getTotalSize() + "},\n" +
                "{valueSize=" + getSizeString(this.value) + "},\n" +
                "{childrenSize=" + this.childrenSize.value + "},\n" +
                "{rightSize=" + getSizeString(this.right) + "},\n" +
                "{leftSize=" + getSizeString(this.left) + "},\n" +
                "{hasLongVal=" + this.hasLongVal + "},\n" +
                "{sharedPrefixPresent=" + this.sharedPrefixPresent + "},\n" +
                "{leftNodePresent=" + this.leftNodePresent + "},\n" +
                "{rightNodePresent=" + this.rightNodePresent + "},\n" +
                "{leftNodeEmbedded=" + this.leftNodeEmbedded + "},\n" +
                "{rightNodeEmbedded=" + this.rightNodeEmbedded + "},\n" +
                "{left=" + HexUtils.toJsonHex(this.left) + "},\n" +
                "{right=" + HexUtils.toJsonHex(this.right) + "},\n" +
                "{hash=" + HexUtils.toJsonHex(this.hash) + "},\n" +
                "{calculateHash()=" + calculateHashString() + "},\n" +
                "{calculateSourceHash()=" + calculateSourceHash() + "},\n" +
                "{leftHash=" + HexUtils.toJsonHex(this.leftHash) + "},\n" +
                "{rightHash=" + HexUtils.toJsonHex(this.rightHash) + "},\n" +
                "{value=" + HexUtils.toJsonHex(this.value) + "},\n" +
                "{toMessage()=" + HexUtils.toJsonHex(this.toMessage()) + "},\n" +
                "{source=" + HexUtils.toJsonHex(this.source) + "}\n";
    }

    /**
     * Based on {@link Trie:toMessage()}
     */
    public byte[] toMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(
                1 + // flags
                        (this.sharedPrefixPresent ? SharedPathSerializer.calculateVarIntSize(this.pathLength) + this.path.length : 0) +
                        serializedLength(leftNodePresent, leftNodeEmbedded, left) +
                        serializedLength(rightNodePresent, rightNodeEmbedded, right) +
                        ((leftNodePresent || rightNodePresent) ? childrenSize.getSizeInBytes() : 0) +
                        (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES + Uint24.BYTES : value.length)
        );
        buffer.put(flags);
        if (this.sharedPrefixPresent) {
            SharedPathSerializer.serializeBytes(buffer, this.pathLength, this.path);
        }
        if (leftNodePresent) {
            if (leftNodeEmbedded) {
                buffer.put(encodeUint8(this.left.length));
                buffer.put(this.left);
            } else {
                buffer.put(this.leftHash);
            }
        }
        if (rightNodePresent) {
            if (rightNodeEmbedded) {
                buffer.put(encodeUint8(this.right.length));
                buffer.put(this.right);
            } else {
                buffer.put(this.rightHash);
            }
        }
        if (leftNodePresent || rightNodePresent) {
            buffer.put(childrenSize.encode());
        }
        if (hasLongVal) {
            byte[] valueHash = new Keccak256(Keccak256Helper.keccak256(getValue())).getBytes();
            buffer.put(valueHash);
            buffer.put(encodeUint24(value.length));
        } else if (this.getValue().length > 0) {
            buffer.put(this.getValue());
        }
        return buffer.array();
    }

    public int serializedLength(boolean isPresent, boolean isEmbeddable, byte[] value) {
        if (isPresent) {
            if (isEmbeddable) {
                return Uint8.BYTES + value.length;
            }
            return Keccak256Helper.DEFAULT_SIZE_BYTES;
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrieDTO trieDTO = (TrieDTO) o;
        return hasLongVal == trieDTO.hasLongVal
                && sharedPrefixPresent == trieDTO.sharedPrefixPresent
                && leftNodePresent == trieDTO.leftNodePresent
                && rightNodePresent == trieDTO.rightNodePresent
                && leftNodeEmbedded == trieDTO.leftNodeEmbedded
                && rightNodeEmbedded == trieDTO.rightNodeEmbedded
                && Arrays.equals(value, trieDTO.value)
                && Objects.equals(childrenSize.value, trieDTO.childrenSize.value)
                && Arrays.equals(path, trieDTO.path);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(hasLongVal, sharedPrefixPresent, leftNodePresent, rightNodePresent, leftNodeEmbedded, rightNodeEmbedded, childrenSize);
        result = 31 * result + Arrays.hashCode(left);
        result = 31 * result + Arrays.hashCode(right);
        result = 31 * result + Arrays.hashCode(value);
        result = 31 * result + Arrays.hashCode(path);
        return result;
    }

    public String calculateHashString() {
        return HexUtils.toJsonHex(calculateHash());
    }

    public byte[] calculateHash() {
        return Keccak256Helper.keccak256(this.toMessage());
    }

    private String calculateSourceHash() {
        if (this.source != null) {
            return HexUtils.toJsonHex(Keccak256Helper.keccak256(this.source));
        }
        return "";
    }

    private String getSizeString(byte[] value) {
        return "" + (value != null ? value.length : 0L);
    }

    private static Function<byte[], byte[]> decodeUint8() {
        return (byte[] lengthBytes) -> {
            Uint8 length = Uint8.decode(lengthBytes, 0);
            return new byte[length.intValue()];
        };
    }

    private static Function<byte[], byte[]> decodeUint24() {
        return (byte[] lengthBytes) -> {
            Uint24 length = Uint24.decode(lengthBytes, 0);
            return new byte[length.intValue()];
        };
    }

    private static byte[] encodeUint24(int length) {
        return new Uint24(length).encode();
    }

    private static byte[] encodeUint8(int length) {
        return new Uint8(length).encode();
    }

}
