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
import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStore;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CLI tool to scan the Rootstock state trie for contracts whose bytecode
 * starts with the 0xEF byte prefix. This is relevant for EIP-3541 / EOF
 * compatibility analysis.
 *
 * <p>The 0xEF prefix is reserved for the EVM Object Format (EOF). EIP-3541
 * rejects new contract deployments starting with 0xEF. This tool verifies
 * that no existing contracts on the network use this prefix.</p>
 *
 * <p><b>Performance:</b> This tool traverses the state trie directly (bypassing
 * the Repository layer) in a single pass at account depth, then for each
 * account does a direct {@code trie.get(codeKey)} lookup. This avoids:
 * <ul>
 *   <li>Allocating a HashSet of all account addresses</li>
 *   <li>The {@code isContract()} lookup per account</li>
 *   <li>The Repository/MutableTrie overhead (locking, tracking)</li>
 *   <li>Traversing any storage subtrees</li>
 * </ul>
 *
 * <p>This is an experimental/unsupported tool.</p>
 *
 * <p>Usage examples:</p>
 * <pre>
 *   # Scan at the best (latest) block
 *   java -cp rskj-core-all.jar co.rsk.cli.tools.ScanEFContracts --mainnet -b best
 *
 *   # Scan at a specific block number
 *   java -cp rskj-core-all.jar co.rsk.cli.tools.ScanEFContracts --mainnet -b 1000000
 * </pre>
 */
@CommandLine.Command(name = "scan-ef-contracts", mixinStandardHelpOptions = true, version = "scan-ef-contracts 1.0",
        description = "Scans the state trie for contracts with bytecode starting with 0xEF byte")
public class ScanEFContracts extends PicoCliToolRskContextAware {

    private static final int EF_BYTE = 0xEF;
    private static final int PROGRESS_LOG_INTERVAL = 10_000;

    /**
     * CODE_PREFIX byte used by TrieKeyMapper to store contract code.
     * Code key = accountKey + CODE_PREFIX.
     */
    private static final byte[] CODE_PREFIX = new byte[]{(byte) 0x80};

    /**
     * Account key bit length: (DOMAIN_PREFIX(1) + SECURE_KEY(10) + ADDRESS(20)) * 8 = 248 bits
     */
    private static final int ACCOUNT_KEY_BIT_LENGTH =
            (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE;

    @CommandLine.Option(names = {"-b", "--block"}, description = "Block number or \"best\"", required = true)
    private String blockArg;

    private final Printer printer;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @SuppressWarnings("unused")
    public ScanEFContracts() { // used via reflection
        this(ScanEFContracts::printInfo);
    }

    @VisibleForTesting
    ScanEFContracts(@Nonnull Printer printer) {
        this.printer = Objects.requireNonNull(printer);
    }

    @Override
    public Integer call() {
        String dbDir = ctx.getRskSystemProperties().databaseDir();
        String dbKind = ctx.getRskSystemProperties().databaseKind().name();
        String network = ctx.getRskSystemProperties().netName();
        printer.println("Database dir: " + dbDir);
        printer.println("Database kind: " + dbKind);
        printer.println("Network: " + network);

        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        Block block = resolveBlock(blockStore, blockArg);

        if (block == null) {
            printer.println("ERROR: Block '" + blockArg + "' not found. "
                    + "Use 'best' or a valid block number within the synced range.");
            Block best = blockStore.getBestBlock();
            if (best != null) {
                printer.println("  Best block available: " + best.getNumber());
            }
            return 1;
        }

        printer.println("=== Scanning for contracts starting with 0xEF byte ===");
        printer.println("Block number: " + block.getNumber());
        printer.println("Block hash:   " + ByteUtil.toHexString(block.getHash().getBytes()));
        printer.println("State root:   " + ByteUtil.toHexString(block.getStateRoot()));

        Optional<Trie> optTrie = trieStore.retrieve(block.getStateRoot());
        if (!optTrie.isPresent()) {
            printer.println("ERROR: State trie not found for the given block. Is the database fully synced?");
            return 1;
        }

        Trie trie = optTrie.get();
        ScanResult result = scanForEFContracts(trie);

        printResults(result, block);

        return 0;
    }

    /**
     * Fast scan of the state trie for contracts starting with 0xEF.
     *
     * <p>Instead of going through the Repository layer (which does 3 separate trie
     * lookups per account: getAccountsKeys + isContract + getCode), this method:
     * <ol>
     *   <li>Iterates the trie at account depth only ({@code stopAtAccountDepth=true}),
     *       which skips all storage subtrees automatically and avoids allocating a
     *       HashSet of all account addresses.</li>
     *   <li>For each account, builds the code key directly
     *       ({@code accountKey + CODE_PREFIX}) and does a single
     *       {@code trie.get(codeKey)} call to retrieve the bytecode.</li>
     *   <li>Checks the first byte of the code for 0xEF.</li>
     * </ol>
     */
    @VisibleForTesting
    ScanResult scanForEFContracts(Trie trie) {
        printer.println("Scanning state trie (single-pass, direct code key lookup)...");

        Iterator<IterationElement> accountIterator = trie.getPreOrderIterator(true);

        List<RskAddress> efContracts = new ArrayList<>();
        int totalAccounts = 0;
        int totalContracts = 0;

        while (accountIterator.hasNext()) {
            IterationElement element = accountIterator.next();
            TrieKeySlice nodeKey = element.getNodeKey();

            // Only process account-level nodes (248 bits deep)
            if (nodeKey.length() != ACCOUNT_KEY_BIT_LENGTH) {
                continue;
            }

            totalAccounts++;

            if (totalAccounts % PROGRESS_LOG_INTERVAL == 0) {
                printer.println("  Scanned " + totalAccounts + " accounts...");
            }

            // Build the code key: accountKeyBytes + CODE_PREFIX(0x80)
            // This is the same key structure used by TrieKeyMapper.getCodeKey()
            byte[] accountKeyBytes = nodeKey.encode();
            byte[] codeKey = ByteUtil.merge(accountKeyBytes, CODE_PREFIX);

            // Direct trie lookup — single O(depth) traversal, no Repository overhead
            byte[] code = trie.get(codeKey);
            if (code == null || code.length == 0) {
                continue; // no code = EOA or empty-code contract
            }

            totalContracts++;

            if ((code[0] & 0xFF) == EF_BYTE) {
                byte[] addressBytes = nodeKey.slice(
                        ACCOUNT_KEY_BIT_LENGTH - RskAddress.LENGTH_IN_BYTES * Byte.SIZE,
                        ACCOUNT_KEY_BIT_LENGTH
                ).encode();
                RskAddress addr = new RskAddress(addressBytes);
                efContracts.add(addr);
                printer.println("  WARNING: Found 0xEF contract at " + addr
                        + " (code size: " + code.length + " bytes"
                        + ", first bytes: " + ByteUtil.toHexString(firstNBytes(code, 8)) + ")");
            }
        }

        printer.println("Scan complete. Scanned " + totalAccounts + " accounts.");

        return new ScanResult(totalAccounts, totalContracts, efContracts);
    }

    private void printResults(ScanResult result, Block block) {
        printer.println("");
        printer.println("=== RESULTS ===");
        printer.println("Block height:                 " + block.getNumber());
        printer.println("Total accounts:               " + result.totalAccounts);
        printer.println("Total contracts:              " + result.totalContracts);
        printer.println("Contracts starting with 0xEF: " + result.efContracts.size());

        if (result.efContracts.isEmpty()) {
            printer.println("PASSED: No contracts found starting with 0xEF byte");
        } else {
            printer.println("FAILED: Found " + result.efContracts.size() + " contract(s) starting with 0xEF:");
            for (RskAddress addr : result.efContracts) {
                printer.println("  - " + addr);
            }
        }
    }

    private static Block resolveBlock(BlockStore blockStore, String blockArg) {
        String arg = blockArg.trim();
        if ("best".equalsIgnoreCase(arg)) {
            return blockStore.getBestBlock();
        }
        return blockStore.getChainBlockByNumber(Long.parseLong(arg));
    }

    private static byte[] firstNBytes(byte[] data, int n) {
        int len = Math.min(data.length, n);
        byte[] result = new byte[len];
        System.arraycopy(data, 0, result, 0, len);
        return result;
    }

    /**
     * Holds the result of the scan operation.
     */
    @VisibleForTesting
    static class ScanResult {
        final int totalAccounts;
        final int totalContracts;
        final List<RskAddress> efContracts;

        ScanResult(int totalAccounts, int totalContracts, List<RskAddress> efContracts) {
            this.totalAccounts = totalAccounts;
            this.totalContracts = totalContracts;
            this.efContracts = efContracts;
        }
    }
}
