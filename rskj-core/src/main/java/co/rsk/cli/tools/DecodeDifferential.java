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
import co.rsk.core.bc.BlockHashesHelper;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
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
import java.util.List;

/**
 * Decode differential over historical transaction bytes: re-decodes and re-encodes every
 * transaction in a block range and checks the result against consensus.
 *
 * Two independent checks per block:
 * - the transactions trie root recomputed from the decoded transactions must equal the root
 *   stored in the header, which anchors the encoding to what the network ratified;
 * - each transaction must survive a further decode/encode cycle unchanged, which localizes a
 *   failure to a single transaction.
 *
 * Emits one JSONL record per block and per transaction. Comparison across two builds is a diff
 * of two streams, not an in-process comparator.
 */
@CommandLine.Command(name = "decode-diff", mixinStandardHelpOptions = true, version = "decode-diff 1.0",
        description = "Re-decodes historical transactions and checks encoding fidelity against consensus")
public class DecodeDifferential extends PicoCliToolRskContextAware {

    private static final int MAX_DUMP_BYTES = 256;

    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-o", "--report"}, description = "JSONL output file (default: stdout)")
    private String reportPath;

    @CommandLine.Option(names = {"--skip-senders"}, description = "Skip sender recovery (much faster)")
    private boolean skipSenders;

    @CommandLine.Option(names = {"--progress-every"}, description = "Log progress every N blocks (default: 10000)")
    private long progressEvery = 10_000L;

    private long blocksVisited;
    private long transactionsVisited;
    private long mismatches;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();
        ActivationConfig activationConfig = ctx.getRskSystemProperties().getActivationConfig();
        SignatureCache signatureCache = ctx.getBlockTxSignatureCache();

        try (Writer writer = openWriter()) {
            visitRange(blockStore, activationConfig, signatureCache, writer);
        }

        printInfo("decode-diff visited {} blocks, {} transactions, {} mismatches",
                blocksVisited, transactionsVisited, mismatches);

        return mismatches == 0 ? 0 : 1;
    }

    private Writer openWriter() throws IOException {
        if (reportPath == null) {
            return new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        }

        return Files.newBufferedWriter(Paths.get(reportPath), StandardCharsets.UTF_8);
    }

    private void visitRange(BlockStore blockStore, ActivationConfig activationConfig,
                            SignatureCache signatureCache, Writer writer) throws IOException {
        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            try {
                // Constructing a Block validates its transactions trie root, so a block whose
                // transactions no longer re-encode faithfully throws here rather than reaching
                // the per-transaction checks. Record it and keep going: that failure is the
                // result, and one block says nothing about the next.
                Block block = blockStore.getChainBlockByNumber(n);
                if (block == null) {
                    writer.write(String.format("{\"kind\":\"block\",\"number\":%d,\"missing\":true}%n", n));
                    blocksVisited++;
                    continue;
                }

                visitBlock(block, activationConfig, signatureCache, writer);
            } catch (RuntimeException e) {
                mismatches++;
                writer.write(String.format("{\"kind\":\"block\",\"number\":%d,\"blockLoadFailed\":true,%s}%n",
                        n, DigestJson.errorFields(e)));
            }

            blocksVisited++;
            if (progressEvery > 0 && blocksVisited % progressEvery == 0) {
                printInfo("decode-diff at block {} ({} transactions, {} mismatches)",
                        n, transactionsVisited, mismatches);
            }
        }
    }

    private void visitBlock(Block block, ActivationConfig activationConfig,
                            SignatureCache signatureCache, Writer writer) throws IOException {
        long number = block.getNumber();
        List<Transaction> transactions = block.getTransactionsList();
        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, number);

        byte[] storedRoot = block.getTxTrieRoot();
        byte[] recomputedRoot = BlockHashesHelper.getTxTrieRoot(transactions, isRskip126Enabled);
        boolean rootOk = Arrays.equals(storedRoot, recomputedRoot);

        if (!rootOk) {
            mismatches++;
        }

        writer.write(String.format(
                "{\"kind\":\"block\",\"number\":%d,\"txCount\":%d,\"txTrieRootOk\":%b,"
                        + "\"storedTxTrieRoot\":\"%s\",\"recomputedTxTrieRoot\":\"%s\"}%n",
                number, transactions.size(), rootOk,
                ByteUtil.toHexString(storedRoot), ByteUtil.toHexString(recomputedRoot)));

        for (int i = 0; i < transactions.size(); i++) {
            visitTransaction(number, i, transactions, signatureCache, writer);
            transactionsVisited++;
        }
    }

    private void visitTransaction(long number, int index, List<Transaction> transactions,
                                  SignatureCache signatureCache, Writer writer) throws IOException {
        Transaction transaction = transactions.get(index);
        boolean remasc = transaction.isRemascTransaction(index, transactions.size());

        byte[] encoded = transaction.getEncoded();
        Transaction reparsed = Transaction.fromRaw(encoded);
        byte[] reencoded = reparsed.getEncoded();

        boolean roundTrip = Arrays.equals(encoded, reencoded);
        boolean hashStable = transaction.getHash().equals(reparsed.getHash());

        if (!roundTrip || !hashStable) {
            mismatches++;
        }

        writer.write(String.format(
                "{\"kind\":\"tx\",\"block\":%d,\"index\":%d,\"type\":\"%s\",\"remasc\":%b,"
                        + "\"hash\":\"%s\",\"sender\":%s,\"size\":%d,\"roundTrip\":%b,\"hashStable\":%b%s}%n",
                number, index, transaction.getType(), remasc,
                transaction.getHash().toHexString(),
                senderField(transaction, remasc, signatureCache),
                encoded.length, roundTrip, hashStable,
                roundTrip ? "" : dumpFields(encoded, reencoded)));
    }

    private String senderField(Transaction transaction, boolean remasc, SignatureCache signatureCache) {
        if (skipSenders || remasc) {
            return "null";
        }

        return "\"" + ByteUtil.toHexString(transaction.getSender(signatureCache).getBytes()) + "\"";
    }

    private static String dumpFields(byte[] encoded, byte[] reencoded) {
        return String.format(",\"encoded\":\"%s\",\"reencoded\":\"%s\"",
                truncatedHex(encoded), truncatedHex(reencoded));
    }

    private static String truncatedHex(byte[] bytes) {
        if (bytes.length <= MAX_DUMP_BYTES) {
            return ByteUtil.toHexString(bytes);
        }

        return ByteUtil.toHexString(Arrays.copyOf(bytes, MAX_DUMP_BYTES)) + "...";
    }
}
