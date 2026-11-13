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
package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.ethereum.core.Rskip546TestSupport.DEFAULT_RECEIVER;
import static org.ethereum.core.Rskip546TestSupport.REGTEST_CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The typed raw parser accepts a transaction only in the exact encoding the node would emit for it:
 * for every input, {@code Transaction.fromRaw} either rejects it or returns a transaction whose
 * {@code getEncoded()} equals the input.
 *
 * <p>Each fixture's RLP tree is rewritten one node at a time — leading zeros, long-form prefixes,
 * the two spellings of zero, a list in place of a byte string and the reverse, and added or removed
 * elements — so the property is checked at every field and nesting level of Type-1, Type-2 and Type-4,
 * including the access list and the authorization tuples.
 */
class TypedRawIngressRoundTripTest {

    @Test
    void typedRawIngressAcceptsOnlyItsOwnEncoding() {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        int accepted = 0;

        for (Map.Entry<String, byte[]> fixture : fixtures().entrySet()) {
            byte[] raw = fixture.getValue();
            assertArrayEquals(raw, Transaction.fromRaw(raw).getEncoded(), fixture.getKey() + " must round-trip");

            Node root = toNode(RLP.decode2(Arrays.copyOfRange(raw, 1, raw.length)).get(0));
            List<List<Integer>> paths = new ArrayList<>();
            collectPaths(root, new ArrayList<>(), paths);

            for (List<Integer> path : paths) {
                for (Map.Entry<String, Function<Node, byte[]>> rewrite : rewrites(nodeAt(root, path)).entrySet()) {
                    byte[] mutated = withTypePrefix(raw[0], encodeWith(root, path, 0, rewrite.getValue()));
                    if (Arrays.equals(mutated, raw)) {
                        continue;
                    }
                    checked++;
                    byte[] reencoded = encodedIfAccepted(mutated);
                    if (reencoded == null) {
                        continue;
                    }
                    accepted++;
                    if (!Arrays.equals(mutated, reencoded)) {
                        mismatches.add(fixture.getKey() + " " + path + " " + rewrite.getKey()
                                + ": received " + Hex.toHexString(mutated)
                                + ", re-encoded " + Hex.toHexString(reencoded));
                    }
                }
            }
        }

        assertTrue(checked > 500, "expected the sweep to cover every node, checked " + checked);
        assertTrue(accepted > 0, "expected some rewrites to be valid transactions");
        assertTrue(mismatches.isEmpty(), mismatches.size() + " accepted inputs re-encode differently, e.g. "
                + mismatches.subList(0, Math.min(5, mismatches.size())));
    }

    /**
     * Parses and re-encodes, or returns {@code null} if the parser rejects the input. Only the parse
     * counts as a rejection: an accepted transaction that then fails to encode fails the test.
     */
    private static byte[] encodedIfAccepted(byte[] raw) {
        Transaction parsed;
        try {
            parsed = Transaction.fromRaw(raw);
        } catch (RuntimeException e) {
            return null;
        }
        return parsed.getEncoded();
    }

    private static Map<String, byte[]> fixtures() {
        Map<String, byte[]> fixtures = new LinkedHashMap<>();

        Transaction type2 = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .nonce(BigInteger.valueOf(128))
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(200))
                .gasLimit(BigInteger.valueOf(32_768))
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.valueOf(128))
                .data(new byte[]{0, 1, 2})
                .accessList(accessList(1, 2))
                .chainId(REGTEST_CHAIN_ID)
                .build();
        type2.sign(PRIVATE_KEY);
        fixtures.put("type2", type2.getEncoded());

        Transaction type2Zero = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .nonce(BigInteger.ZERO)
                .maxPriorityFeePerGas(Coin.ZERO)
                .maxFeePerGas(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.ZERO)
                .accessList(RLP.encodeList())
                .chainId(REGTEST_CHAIN_ID)
                .build();
        type2Zero.sign(PRIVATE_KEY);
        fixtures.put("type2-zero-scalars", type2Zero.getEncoded());

        Transaction type2Unsigned = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .nonce(BigInteger.ONE)
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(2))
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.ONE)
                .accessList(RLP.encodeList())
                .chainId(REGTEST_CHAIN_ID)
                .build();
        fixtures.put("type2-unsigned", type2Unsigned.getEncoded());

        Transaction type1 = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .nonce(BigInteger.valueOf(7))
                .gasPrice(BigInteger.valueOf(60_000_000L))
                .gasLimit(BigInteger.valueOf(50_000))
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.valueOf(1_000))
                .accessList(accessList(2, 1))
                .chainId(REGTEST_CHAIN_ID)
                .build();
        type1.sign(PRIVATE_KEY);
        fixtures.put("type1", type1.getEncoded());

        Transaction type1Create = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .nonce(BigInteger.valueOf(8))
                .gasPrice(BigInteger.valueOf(60_000_000L))
                .gasLimit(BigInteger.valueOf(90_000))
                .value(BigInteger.ZERO)
                .data(new byte[]{0x60, 0x00})
                .accessList(RLP.encodeList())
                .chainId(REGTEST_CHAIN_ID)
                .build();
        type1Create.sign(PRIVATE_KEY);
        fixtures.put("type1-create", type1Create.getEncoded());

        ECKey authority = ECKey.fromPrivate(HashUtil.keccak256("authority".getBytes()));
        Transaction type4 = Rskip545TestSupport.unsignedType4WithAuthorizations(
                DEFAULT_RECEIVER,
                BigInteger.valueOf(100_000),
                List.of(Rskip545TestSupport.createSignedAuthorization(
                        authority, DEFAULT_RECEIVER, BigInteger.ONE, REGTEST_CHAIN_ID)));
        type4.sign(PRIVATE_KEY);
        fixtures.put("type4", type4.getEncoded());

        return fixtures;
    }

    private static byte[] accessList(int entries, int keysPerEntry) {
        byte[][] encodedEntries = new byte[entries][];
        for (int i = 0; i < entries; i++) {
            byte[] address = new byte[20];
            address[0] = 0x55;
            address[19] = (byte) i;
            byte[][] keys = new byte[keysPerEntry][];
            for (int k = 0; k < keysPerEntry; k++) {
                byte[] key = new byte[32];
                key[0] = (byte) 0x80;
                key[31] = (byte) k;
                keys[k] = RLP.encodeElement(key);
            }
            encodedEntries[i] = RLP.encodeList(RLP.encodeElement(address), RLP.encodeList(keys));
        }
        return RLP.encodeList(encodedEntries);
    }

    // ----- RLP tree and single-node rewrites -----

    private sealed interface Node permits Item, Items {}

    private record Item(byte[] data) implements Node {}

    private record Items(List<Node> children) implements Node {}

    private static Node toNode(RLPElement element) {
        if (element instanceof RLPList list) {
            List<Node> children = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                children.add(toNode(list.get(i)));
            }
            return new Items(children);
        }
        byte[] data = element.getRLPData();
        return new Item(data == null ? new byte[0] : data);
    }

    private static byte[] encode(Node node) {
        if (node instanceof Item item) {
            return RLP.encodeElement(item.data());
        }
        return RLP.encodeList(encodeChildren((Items) node));
    }

    private static byte[][] encodeChildren(Items items) {
        byte[][] encoded = new byte[items.children().size()][];
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = encode(items.children().get(i));
        }
        return encoded;
    }

    /** Encodes the tree canonically except for the node at {@code path}, which the rewrite spells. */
    private static byte[] encodeWith(Node node, List<Integer> path, int depth, Function<Node, byte[]> rewrite) {
        if (depth == path.size()) {
            return rewrite.apply(node);
        }
        Items items = (Items) node;
        byte[][] encoded = encodeChildren(items);
        int index = path.get(depth);
        encoded[index] = encodeWith(items.children().get(index), path, depth + 1, rewrite);
        return RLP.encodeList(encoded);
    }

    private static void collectPaths(Node node, List<Integer> current, List<List<Integer>> out) {
        out.add(new ArrayList<>(current));
        if (node instanceof Items items) {
            for (int i = 0; i < items.children().size(); i++) {
                current.add(i);
                collectPaths(items.children().get(i), current, out);
                current.remove(current.size() - 1);
            }
        }
    }

    private static Node nodeAt(Node root, List<Integer> path) {
        Node node = root;
        for (int index : path) {
            node = ((Items) node).children().get(index);
        }
        return node;
    }

    private static Map<String, Function<Node, byte[]>> rewrites(Node node) {
        Map<String, Function<Node, byte[]>> rewrites = new LinkedHashMap<>();
        if (node instanceof Item item) {
            byte[] data = item.data();
            rewrites.put("leading zero", n -> RLP.encodeElement(concat(new byte[]{0}, data)));
            if (data.length == 1 && (data[0] & 0xff) < 0x80) {
                rewrites.put("single byte with a prefix", n -> new byte[]{(byte) 0x81, data[0]});
            }
            if (data.length < 56) {
                rewrites.put("long-form string prefix", n -> concat(new byte[]{(byte) 0xb8, (byte) data.length}, data));
                rewrites.put("list frame around the payload", n -> concat(new byte[]{(byte) (0xc0 + data.length)}, data));
            }
            if (data.length == 0) {
                rewrites.put("zero as 0x00", n -> new byte[]{0});
                rewrites.put("zero as 0x81 0x00", n -> new byte[]{(byte) 0x81, 0});
            } else {
                rewrites.put("empty string", n -> new byte[]{(byte) 0x80});
                rewrites.put("first byte dropped", n -> RLP.encodeElement(Arrays.copyOfRange(data, 1, data.length)));
            }
            rewrites.put("wrapped in a list", n -> RLP.encodeList(RLP.encodeElement(data)));
        } else {
            Items items = (Items) node;
            byte[] payload = concat(encodeChildren(items));
            if (payload.length < 56) {
                rewrites.put("long-form list prefix", n -> concat(new byte[]{(byte) 0xf8, (byte) payload.length}, payload));
            }
            rewrites.put("string frame around the payload", n -> RLP.encodeElement(payload));
            rewrites.put("wrapped in a list", n -> RLP.encodeList(encode(items)));
            rewrites.put("empty string appended", n -> RLP.encodeList(payload, new byte[]{(byte) 0x80}));
            if (!items.children().isEmpty()) {
                rewrites.put("last element dropped", n -> {
                    List<Node> fewer = new ArrayList<>(items.children());
                    fewer.remove(fewer.size() - 1);
                    return encode(new Items(fewer));
                });
            }
        }
        return rewrites;
    }

    private static byte[] withTypePrefix(byte type, byte[] body) {
        return concat(new byte[]{type}, body);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
