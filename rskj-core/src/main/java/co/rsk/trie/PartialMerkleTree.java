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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 07/02/2017.
 */
public class PartialMerkleTree {
    private List<Trie> tries;
    private List<Integer> insertions;

    public PartialMerkleTree(Trie trie) {
        this.tries = new ArrayList<>();
        this.insertions = new ArrayList<>();
        Trie clone = trie.cloneTrie();
        clone.removeValue();
        this.tries.add(clone);
    }

    public void addTrie(Trie trie, int insertion) {
        // normalize trie, to have all hashes
        Trie clone = trie.cloneTrie();
        clone.removeNode(insertion);

        this.tries.add(clone);
        this.insertions.add(insertion);
    }

    public byte[] getHash(byte[] value) {
        Trie trie = this.tries.get(0).cloneTrie(value);
        int ntries = this.tries.size();

        for (int k = 1; k < ntries; k++) {
            Trie clone = this.tries.get(k).cloneTrie();
            clone.setHash(this.insertions.get(k - 1).intValue(), trie.getHash().getBytes());
            trie = clone;
        }

        return trie.getHash().getBytes();
    }

    public byte[] toMessage() {
        List<byte[]> trieMessages = new ArrayList<>();

        for (Trie trie : this.tries) {
            trieMessages.add(trie.toMessage());
        }

        int nmessages = this.tries.size();
        int lmessages = 0;

        for (byte[] msg : trieMessages) {
            lmessages += msg.length;
        }

        byte[] message = new byte[1 + lmessages + 4 * nmessages + (nmessages - 1)];

        message[0] = (byte)nmessages;

        int position = 1;

        for (int k = 0; k < nmessages; k++) {
            byte[] msg = trieMessages.get(k);
            int lmsg = msg.length;

            if (k > 0) {
                message[position++] = this.insertions.get(k - 1).byteValue();
            }

            message[position] = (byte)((lmsg >> 24) & 0x00ff);
            message[position + 1] = (byte)((lmsg >> 16) & 0x00ff);
            message[position + 2] = (byte)((lmsg >> 8) & 0x00ff);
            message[position + 3] = (byte)(lmsg & 0x00ff);

            System.arraycopy(msg, 0, message, position + 4, lmsg);

            position += 4 + lmsg;
        }

        return message;
    }

    public static PartialMerkleTree fromMessage(byte[] message) {
        int ntries = message[0];

        PartialMerkleTree tree = null;

        int position = 1;

        for (int k = 0; k < ntries; k++) {
            int insertion = 0;

            if (k > 0) {
                insertion = message[position++];
            }

            int lmsg = getInteger(message, position);
            byte[] trieMsg = new byte[lmsg];
            System.arraycopy(message, position + 4, trieMsg, 0, lmsg);
            TrieImpl trie = TrieImpl.fromMessage(trieMsg, null);

            if (k == 0) {
                tree = new PartialMerkleTree(trie);
            } else if (tree == null) {
                throw new NullPointerException();
            } else {
                tree.addTrie(trie, insertion);
            }

            position += 4 + lmsg;
        }

        return tree;
    }

    private static int getInteger(byte[] message, int position) {
        int value = 0;

        for (int k = 0; k < 4; k++) {
            value <<= 8;

            value += message[position + k] & 0x00ff;
        }

        return value;
    }
}
