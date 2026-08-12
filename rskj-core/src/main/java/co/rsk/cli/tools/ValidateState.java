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
import co.rsk.crypto.Keccak256;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The entry point for the validate state CLI util.
 * <p>
 * Walks the state trie of a block and reports every referenced node or long value that is not present in
 * the state database, so an incomplete state can be detected up front rather than at first state access.
 * A state database populated out of band — restored from a snapshot, imported from bootstrap data, or
 * copied between nodes — can be missing pieces the block headers give no hint about.
 * <p>
 * What this checks and what it does not: the walk asserts <b>presence</b>. Every node and long value the
 * trie references must be retrievable from the store. It does <b>not</b> re-hash stored bytes to check they
 * match the key they are filed under, so a report of "complete" means nothing is missing, not that the
 * state root is correct.
 * <p>
 * The walk drives itself off {@link TrieStore#retrieve(byte[])} rather than {@link NodeReference#getNode()},
 * because a reference deserialized from the store stops the JVM when its node cannot be retrieved — which
 * is the very condition this tool has to survive and report. Nodes are not memoized as they are visited, so
 * peak retention stays proportional to the trie depth rather than to the size of the state.
 * <p>
 * This is an experimental/unsupported tool.
 * <p>
 * Required cli args:
 * - args[0] - block number or "best" (defaults to "best")
 */
@CommandLine.Command(name = "validate-state", mixinStandardHelpOptions = true, version = "validate-state 1.0",
        description = "Validates that every node and long value the state trie of a block references is present in the state database")
public class ValidateState extends PicoCliToolRskContextAware {

    /** How many individual gaps are listed in the report; the total count is always reported in full. */
    private static final int MAX_REPORTED_GAPS = 20;

    @CommandLine.Option(names = {"-b", "--block"}, description = "block number or \"best\" (default: ${DEFAULT-VALUE})",
            defaultValue = "best")
    private String blockNum;

    private final Printer printer;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @SuppressWarnings("unused")
    public ValidateState() { // used via reflection
        this(ValidateState::printInfo);
    }

    ValidateState(@Nonnull Printer printer) {
        this.printer = Objects.requireNonNull(printer);
    }

    @Override
    public Integer call() {
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        Block block;
        try {
            block = resolveBlock(blockStore);
        } catch (NumberFormatException e) {
            // An unparseable --block is a usage error, not a missing block; keep the two distinct so the
            // message tells the operator which one they hit.
            printer.println("Invalid block: " + blockNum + " (expected a block number or \"best\")");
            return 1;
        }

        if (block == null) {
            printer.println("Block not found: " + blockNum);
            return 1;
        }

        // RSKIP126 remapped the header's state root, so the root to look up in the store is the translated
        // one, not the raw header field.
        Keccak256 stateRoot = ctx.getStateRootHandler().translate(block.getHeader());

        printer.println("Block number: " + block.getNumber());
        printer.println("Block hash: " + ByteUtil.toHexString(block.getHash().getBytes()));
        printer.println("State root: " + stateRoot.toHexString());

        Optional<Trie> root = retrieveNode(trieStore, stateRoot);
        if (!root.isPresent()) {
            printer.println("State root does not resolve to a node in the state database");
            printer.println("State is INCOMPLETE");
            return 1;
        }

        Report report = new Report();
        walk(trieStore, root.get(), report);

        printer.println("Trie nodes visited: " + report.nodes);
        printer.println("Embedded nodes visited: " + report.embeddedNodes);
        printer.println("Long values visited: " + report.longValues);
        printer.println("Gaps found: " + report.gaps);

        report.reported.forEach(printer::println);
        if (report.gaps > report.reported.size()) {
            printer.println("... and " + (report.gaps - report.reported.size()) + " more");
        }

        if (report.gaps > 0) {
            printer.println("State is INCOMPLETE");
            return 1;
        }

        printer.println("State is complete");
        return 0;
    }

    private Block resolveBlock(BlockStore blockStore) {
        if ("best".equals(blockNum)) {
            return blockStore.getBestBlock();
        }

        return blockStore.getChainBlockByNumber(Long.parseLong(blockNum));
    }

    /**
     * Visits {@code trie}, probes its long value if it has one, and recurses into both children. A gap ends
     * that subtree only — the walk carries on with the siblings so one report names every gap it can reach,
     * instead of stopping at the first.
     */
    private void walk(TrieStore trieStore, Trie trie, Report report) {
        report.nodes++;

        if (trie.hasLongValue()) {
            // A long value is referenced by hash and read lazily: deserializing the node that references it
            // does not touch it, so it has to be probed explicitly or a missing one goes unnoticed.
            Keccak256 valueHash = trie.getValueHash();
            if (retrieveValue(trieStore, valueHash) == null) {
                report.recordGap("missing long value: " + valueHash.toHexString());
            } else {
                report.longValues++;
            }
        }

        walkChild(trieStore, trie.getLeft(), report);
        walkChild(trieStore, trie.getRight(), report);
    }

    private void walkChild(TrieStore trieStore, NodeReference reference, Report report) {
        if (reference.isEmpty()) {
            return;
        }

        if (reference.wasLoaded()) {
            // An embedded child is serialized inline in its parent's message and rehydrated in memory, and
            // is deliberately not stored under its own hash. Looking it up in the store would report a gap
            // for every one of them, so it is traversed in memory instead. Reading the node back off an
            // already-loaded reference touches no store and cannot stop the JVM.
            report.embeddedNodes++;
            reference.getNode().ifPresent(child -> walk(trieStore, child, report));
            return;
        }

        Optional<Keccak256> hash = reference.getHash();
        if (!hash.isPresent()) {
            return;
        }

        Optional<Trie> child = retrieveNode(trieStore, hash.get());
        if (!child.isPresent()) {
            report.recordGap("missing node: " + hash.get().toHexString());
            return;
        }

        walk(trieStore, child.get(), report);
    }

    /**
     * Retrieves a node, mapping a failure to retrieve it onto an empty result. Legacy (Orchid) node messages
     * resolve their long value while being deserialized, so a node whose value is missing can fail by
     * throwing here rather than by returning empty.
     */
    private static Optional<Trie> retrieveNode(TrieStore trieStore, Keccak256 hash) {
        try {
            return trieStore.retrieve(hash.getBytes());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static byte[] retrieveValue(TrieStore trieStore, Keccak256 hash) {
        try {
            return trieStore.retrieveValue(hash.getBytes());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static class Report {
        private long nodes;
        private long embeddedNodes;
        private long longValues;
        private long gaps;
        private final List<String> reported = new ArrayList<>();

        private void recordGap(String description) {
            gaps++;
            if (reported.size() < MAX_REPORTED_GAPS) {
                reported.add(description);
            }
        }
    }
}
