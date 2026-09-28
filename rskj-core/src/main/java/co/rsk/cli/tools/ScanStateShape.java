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
package co.rsk.cli.tools;

import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

/**
 * State-shape precondition scan for RSKIP-545: reports every account whose stored code activation
 * would retroactively reinterpret as a delegation designator.
 *
 * Two signals, counted separately. The exact one is {@link DelegationCodeResolver#isDelegatedCode}
 * — code of exactly 23 bytes starting with {@code 0xef 0x01 0x00} — which is what
 * {@code getExecutionCode} runs on every code resolution once RSKIP-545 is active. The weaker one
 * is any code starting with {@code 0xEF}, which bounds how close the deployed set is to that
 * boundary without matching it.
 *
 * The walk is the one in {@link ValidateState}, with the trie key tracked alongside so a code node
 * can be mapped back to its account, and with storage subtrees pruned since no code key lives
 * under one.
 *
 * Emits one JSONL record per finding plus a summary record. Exits non-zero when the exact-match set
 * is non-empty, and also when the walk hit a gap, because a scan that could not read part of the
 * trie has not answered the question.
 */
@CommandLine.Command(name = "scan-state-shape", mixinStandardHelpOptions = true, version = "scan-state-shape 1.0",
        description = "Scans the state trie for accounts whose code RSKIP-545 would reinterpret as a delegation designator")
public class ScanStateShape extends PicoCliToolRskContextAware {

    /** Bits consumed by an account key: domain prefix, secure prefix and the address. */
    private static final int ACCOUNT_KEY_BITS =
            (TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_ACCOUNT_KEY_SIZE) * 8;
    private static final int CODE_KEY_BITS = ACCOUNT_KEY_BITS + 8;
    private static final int ADDRESS_BIT_OFFSET =
            (TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE) * 8;
    private static final int ADDRESS_BITS = RskAddress.LENGTH_IN_BYTES * 8;

    /** Must stay in step with TrieKeyMapper's code prefix; the storage prefix is its 0x00 counterpart. */
    private static final byte CODE_PREFIX = (byte) 0x80;

    private static final int MAX_DUMP_BYTES = 256;
    private static final int INITIAL_PATH_BITS = 1024;

    @CommandLine.Option(names = {"-b", "--block"}, description = "block number or \"best\" (default: ${DEFAULT-VALUE})",
            defaultValue = "best")
    private String blockNum;

    @CommandLine.Option(names = {"-o", "--report"}, description = "JSONL output file (default: stdout)")
    private String reportPath;

    @CommandLine.Option(names = {"--progress-every"}, description = "Log progress every N trie nodes (default: 1000000)")
    private long progressEvery = 1_000_000L;

    private byte[] path = new byte[INITIAL_PATH_BITS];

    private long nodesVisited;
    private long accounts;
    private long accountsWithCode;
    private long delegationShaped;
    private long efPrefixed;
    private long gaps;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        Block block;
        try {
            block = resolveBlock(blockStore);
        } catch (NumberFormatException e) {
            printInfo("Invalid block: {} (expected a block number or \"best\")", blockNum);
            return 1;
        }

        if (block == null) {
            printInfo("Block not found: {}", blockNum);
            return 1;
        }

        // RSKIP126 remapped the header's state root, so the root to look up is the translated one.
        Keccak256 stateRoot = ctx.getStateRootHandler().translate(block.getHeader());

        try (Writer writer = openWriter()) {
            Optional<Trie> root = retrieveNode(trieStore, stateRoot);
            if (root.isPresent()) {
                walk(trieStore, root.get(), 0, writer);
            } else {
                recordGap(writer, "stateRoot", stateRoot);
            }

            writeSummary(writer, block, stateRoot);
        }

        printInfo("scan-state-shape at block {} visited {} accounts ({} with code): {} delegation-shaped, "
                        + "{} 0xEF-prefixed, {} gaps",
                block.getNumber(), accounts, accountsWithCode, delegationShaped, efPrefixed, gaps);

        return delegationShaped == 0 && gaps == 0 ? 0 : 1;
    }

    private Block resolveBlock(BlockStore blockStore) {
        if ("best".equals(blockNum)) {
            return blockStore.getBestBlock();
        }

        return blockStore.getChainBlockByNumber(Long.parseLong(blockNum));
    }

    private Writer openWriter() throws IOException {
        if (reportPath == null) {
            return new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        }

        return Files.newBufferedWriter(Paths.get(reportPath), StandardCharsets.UTF_8);
    }

    /**
     * Visits {@code trie} and recurses into both children, carrying the key bits consumed so far so a
     * terminal value can be attributed to an account. A gap ends that subtree only.
     */
    private void walk(TrieStore trieStore, Trie trie, int depth, Writer writer) throws IOException {
        nodesVisited++;
        if (progressEvery > 0 && nodesVisited % progressEvery == 0) {
            printInfo("scan-state-shape visited {} trie nodes ({} accounts, {} delegation-shaped)",
                    nodesVisited, accounts, delegationShaped);
        }

        int end = appendSharedPath(trie, depth);

        // Everything below an account's storage prefix is storage, and no code key lives there.
        if (end > ACCOUNT_KEY_BITS && path[ACCOUNT_KEY_BITS] == 0) {
            return;
        }

        if (trie.getValueLength().intValue() > 0) {
            visitValue(trieStore, trie, end, writer);
        }

        walkChild(trieStore, trie.getLeft(), (byte) 0, end, writer);
        walkChild(trieStore, trie.getRight(), (byte) 1, end, writer);
    }

    private void walkChild(TrieStore trieStore, NodeReference reference, byte implicitBit, int depth, Writer writer)
            throws IOException {
        if (reference.isEmpty()) {
            return;
        }

        ensureCapacity(depth + 1);
        path[depth] = implicitBit;

        if (reference.wasLoaded()) {
            // An embedded child is serialized inline in its parent and is deliberately not stored under its
            // own hash, so it is traversed in memory rather than looked up.
            Optional<Trie> child = reference.getNode();
            if (child.isPresent()) {
                walk(trieStore, child.get(), depth + 1, writer);
            }
            return;
        }

        Optional<Keccak256> hash = reference.getHash();
        if (!hash.isPresent()) {
            return;
        }

        Optional<Trie> child = retrieveNode(trieStore, hash.get());
        if (!child.isPresent()) {
            recordGap(writer, "node", hash.get());
            return;
        }

        walk(trieStore, child.get(), depth + 1, writer);
    }

    private void visitValue(TrieStore trieStore, Trie trie, int pathLength, Writer writer) throws IOException {
        if (pathLength == ACCOUNT_KEY_BITS) {
            accounts++;
            return;
        }

        if (pathLength != CODE_KEY_BITS || packBits(ACCOUNT_KEY_BITS, 8)[0] != CODE_PREFIX) {
            return;
        }

        accountsWithCode++;

        byte[] code = readValue(trie);
        if (code == null) {
            recordGap(writer, "code", trie.getValueHash());
            return;
        }

        boolean delegationShape = DelegationCodeResolver.isDelegatedCode(code);
        boolean efPrefix = code.length > 0 && code[0] == DelegationCodeResolver.DELEGATION_PREFIX[0];

        if (!delegationShape && !efPrefix) {
            return;
        }

        if (delegationShape) {
            delegationShaped++;
        }
        if (efPrefix) {
            efPrefixed++;
        }

        RskAddress address = new RskAddress(packBits(ADDRESS_BIT_OFFSET, ADDRESS_BITS));
        writer.write(String.format(
                "{\"kind\":\"account\",\"address\":\"%s\",\"codeLength\":%d,\"code\":\"%s\","
                        + "\"delegationShape\":%b,\"efPrefixed\":%b,\"delegatesTo\":%s}%n",
                address.toHexString(), code.length, truncatedHex(code), delegationShape, efPrefix,
                delegationShape ? "\"" + DelegationCodeResolver.extractDelegatedAddress(code).toHexString() + "\"" : "null"));
    }

    private void writeSummary(Writer writer, Block block, Keccak256 stateRoot) throws IOException {
        writer.write(String.format(
                "{\"kind\":\"summary\",\"block\":%d,\"stateRoot\":\"%s\",\"nodes\":%d,\"accounts\":%d,"
                        + "\"accountsWithCode\":%d,\"delegationShaped\":%d,\"efPrefixed\":%d,\"gaps\":%d,"
                        + "\"complete\":%b}%n",
                block.getNumber(), stateRoot.toHexString(), nodesVisited, accounts, accountsWithCode,
                delegationShaped, efPrefixed, gaps, gaps == 0));
    }

    private void recordGap(Writer writer, String kind, Keccak256 hash) throws IOException {
        gaps++;
        writer.write(String.format("{\"kind\":\"gap\",\"of\":\"%s\",\"hash\":%s}%n",
                kind, DigestJson.quoteOrNull(hash == null ? null : hash.toHexString())));
    }

    /** Copies this node's shared path into the key buffer and returns the key length it ends at. */
    private int appendSharedPath(Trie trie, int depth) {
        TrieKeySlice sharedPath = trie.getSharedPath();
        int length = sharedPath.length();

        ensureCapacity(depth + length);
        for (int i = 0; i < length; i++) {
            path[depth + i] = sharedPath.get(i);
        }

        return depth + length;
    }

    private void ensureCapacity(int bits) {
        if (bits > path.length) {
            path = Arrays.copyOf(path, Math.max(bits, path.length * 2));
        }
    }

    /** Packs {@code bitLength} expanded key bits, most significant first, back into bytes. */
    private byte[] packBits(int from, int bitLength) {
        byte[] packed = new byte[bitLength / 8];

        for (int i = 0; i < bitLength; i++) {
            if (path[from + i] != 0) {
                packed[i / 8] |= (byte) (0x80 >>> (i % 8));
            }
        }

        return packed;
    }

    private static byte[] readValue(Trie trie) {
        try {
            return trie.getValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Retrieves a node, mapping a failure to retrieve it onto an empty result: a legacy node message
     * resolves its long value while being deserialized, so a missing value can fail by throwing here.
     */
    private static Optional<Trie> retrieveNode(TrieStore trieStore, Keccak256 hash) {
        try {
            return trieStore.retrieve(hash.getBytes());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String truncatedHex(byte[] bytes) {
        if (bytes.length <= MAX_DUMP_BYTES) {
            return ByteUtil.toHexString(bytes);
        }

        return ByteUtil.toHexString(Arrays.copyOf(bytes, MAX_DUMP_BYTES)) + "...";
    }
}
