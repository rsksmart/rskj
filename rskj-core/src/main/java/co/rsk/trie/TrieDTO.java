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
import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.core.types.ints.Uint8;
import co.rsk.util.HexUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.TrieKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

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

    private byte[] left;
    private byte[] right;
    private byte[] leftHash;
    private byte[] rightHash;
    private byte[] encoded;
    private byte[] source;
    private byte[] value;
    // the size of this node along with its children (in bytes)
    // note that we use a long because an int would allow only up to 4GB of state to be stored.
    private VarInt childrenSize;
    private byte[] path;


    private TrieDTO() {
    }

    public static TrieDTO decode(byte[] src, TrieStore ds) {
        TrieDTO result = new TrieDTO();
        try {
            ByteArrayOutputStream encoder = new ByteArrayOutputStream();
            ByteBuffer srcWrap = ByteBuffer.wrap(src);
            byte flags = srcWrap.get();
            //1.flags
            encoder.write(flags);
            result.hasLongVal = (flags & 0b00100000) == 0b00100000;
            result.sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
            result.leftNodePresent = (flags & 0b00001000) == 0b00001000;
            result.rightNodePresent = (flags & 0b00000100) == 0b00000100;
            result.leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
            result.rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;

            //(*optional) 2.sharedPath - if sharedPrefixPresent
            result.path = SharedPathSerializer.deserializeBytes(srcWrap, result.sharedPrefixPresent, encoder);

            //(*optional) 3.left - if present & !embedded => hash
            byte[] child =  readChild(srcWrap, result.leftNodePresent, result.leftNodeEmbedded, encoder);
            if(result.leftNodePresent && result.leftNodeEmbedded) {
                result.left = child;
                result.leftHash = null;
            } else {
                result.left = null;
                result.leftHash = child;
            }

            //(*optional) 3.right - if present & !embedded => hash
            child = readChild(srcWrap, result.rightNodePresent, result.rightNodeEmbedded, encoder);
            if(result.rightNodePresent && result.rightNodeEmbedded) {
                result.right = child;
                result.rightHash = null;
            } else {
                result.right = null;
                result.rightHash = child;
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
            result.encoded = encoder.toByteArray();
            result.source = ArrayUtils.clone(src);
        } catch (IOException e) {
            logger.trace("Error while decoding: {}", e.getMessage());
        }
        return result;
    }

    private static byte[] readChild(ByteBuffer srcWrap, boolean present, boolean embedded, ByteArrayOutputStream encoder) throws IOException {
        if (present) {
            if (embedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                srcWrap.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);
                byte[] serializedNode = new byte[length.intValue()];
                srcWrap.get(serializedNode);
                //TODO improve what we send for embedded nodes.
                encoder.write(lengthBytes);
                encoder.write(serializedNode);
                return serializedNode;
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                srcWrap.get(valueHash);
                return valueHash;
            }
        }
        return new byte[0];
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107
     * <p>
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * It shouldn't be called from outside. It's still public for NodeReference call
     */
    public VarInt getChildrenSize() {
        if (childrenSize == null) {
            if (!(this.leftNodePresent || this.rightNodePresent)) {
                childrenSize = new VarInt(0);
            } else {
                // TODO: fix this method
                //childrenSize = new VarInt(this.getLeft().referenceSize() + this.right.referenceSize());
            }
        }

        return childrenSize;
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
        encoder.write(bytes);
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
        return !(this.leftNodePresent || this.rightNodePresent);
    }

    public boolean isAccountLevel() {
        return this.path.length >= (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES);
    }

    public byte[] getLeft() {
        return left;
    }

    public byte[] getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "Node{" + HexUtils.toJsonHex(this.path)+"}:"+ this.childrenSize.value ;
    }
    public String toDescription() {
        return "Node{" + HexUtils.toJsonHex(this.path)+"}:\n" +
                "{isTerminal()=" + this.isTerminal() + "},\n" +
                "{isAccountLevel()=" + this.isAccountLevel() + "},\n" +
                "{childrenSize=" + this.childrenSize.value + "},\n" +
                "{hasLongVal=" + this.hasLongVal + "},\n" +
                "{decoded=" + HexUtils.toJsonHex(TrieKeySlice.fromKey(this.path).expand()) + "},\n" +
                "{value=" + HexUtils.toJsonHex(this.value) + "}\n";
    }
}
