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
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replays a block range under the node's current activation config and emits a per-block digest
 * stream, so two builds can be compared by diffing two streams rather than by an in-process
 * comparator.
 *
 * Execution is anchored at each block's own parent, so a divergence at one block does not
 * cascade into the next and the per-block signal stays precise.
 *
 * Scenario {@code fidelity} additionally checks every computed value against the stored header.
 * The chain is the oracle there: those values were produced by the previous code and ratified by
 * the network, so no second jar is needed.
 *
 * Scenario {@code backtest} runs with a rule forced on from a past height, where divergence is
 * the expected output: only a diverging block of pre-existing transaction types is a failure.
 *
 * Activation heights are overridden through the existing {@code -X} mechanism, for example
 * {@code -Xblockchain.config.consensusRules.rskip545=0}.
 */
@CommandLine.Command(name = "consensus-replay", mixinStandardHelpOptions = true, version = "consensus-replay 1.0",
        description = "Replays a block range and emits a per-block consensus digest stream")
public class ConsensusReplay extends PicoCliToolRskContextAware {

    public enum Scenario {
        /** Compare every computed value against the stored header. */
        FIDELITY,
        /** Emit digests only; comparison is a diff against another run's stream. */
        DIGEST,
        /** Execute each block twice and require the consensus fields to agree. */
        REPEAT,
        /** Replay with a rule forced on from a past height and measure the divergence set. */
        BACKTEST
    }

    /** Rules that change header format or hashing, so every stored header would diverge for an
     * unrelated reason. See ActivationConfig.getHeaderVersion. */
    private static final Set<ConsensusRule> NOT_BACKTESTABLE = EnumSet.of(ConsensusRule.RSKIP351, ConsensusRule.RSKIP535);

    /** Transaction types each rule introduces; a block carrying none of them is legacy-only.
     * A rule absent here introduces none, which makes the P1 check stricter, never weaker. */
    private static final Map<ConsensusRule, Set<TransactionType>> TYPES_INTRODUCED = Map.of(
            ConsensusRule.RSKIP545, EnumSet.of(TransactionType.TYPE_4),
            ConsensusRule.RSKIP546, EnumSet.of(TransactionType.TYPE_1, TransactionType.TYPE_2));

    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-s", "--scenario"}, description = "Scenario: ${COMPLETION-CANDIDATES}")
    private Scenario scenario = Scenario.FIDELITY;

    @CommandLine.Option(names = {"-o", "--report"}, description = "JSONL output file (default: stdout)")
    private String reportPath;

    @CommandLine.Option(names = {"--save-state"},
            description = "Persist executed state. Off by default: forced-activation runs would corrupt the snapshot")
    private boolean saveState;

    @CommandLine.Option(names = {"--progress-every"}, description = "Log progress every N blocks (default: 10000)")
    private long progressEvery = 10_000L;

    @CommandLine.Option(names = {"--resume"},
            description = "Continue an interrupted run: drop any partial trailing record from --report and restart after its last complete block")
    private boolean resume;

    @CommandLine.Option(names = {"--rule"},
            description = "BACKTEST only: consensus rule config key forced on, e.g. rskip545")
    private String ruleKey;

    @CommandLine.Option(names = {"--force-at"},
            description = "BACKTEST only: height from which --rule is expected to be active")
    private Long forceAt;

    private ConsensusRule forcedRule;

    private long blocksVisited;
    private long mismatches;
    private long divergentBlocks;
    private long p1Violations;
    private long invariantViolations;

    // Last nonce seen per sender across the whole range; a nonce that fails to advance is a bug.
    private final Map<RskAddress, BigInteger> lastNonce = new HashMap<>();

    // Phase timings. A slowdown is only actionable if it can be attributed, and per-block
    // execution time alone cannot distinguish the executor from block loading or host contention.
    private long fetchNanos;
    private long executeNanos;
    private long digestNanos;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        ActivationConfig activationConfig = ctx.getRskSystemProperties().getActivationConfig();

        // Checked before anything opens the database, so a refused run costs nothing.
        String refusal = refusalReason(activationConfig);
        if (refusal != null) {
            printError("consensus-replay refused: {}", refusal);
            try (Writer writer = openWriter()) {
                writer.write(String.format("{\"kind\":\"refusal\",\"scenario\":\"%s\",\"reason\":%s}%n",
                        scenario, DigestJson.quoteOrNull(refusal)));
            }

            return 1;
        }

        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();
        TrieStore trieStore = ctx.getTrieStore();

        if (resume) {
            fromBlockNumber = resumeFrom();
        }

        try (Writer writer = openWriter()) {
            replayRange(blockExecutor, blockStore, stateRootHandler, activationConfig, trieStore, writer);
        }

        if (saveState) {
            ctx.getTrieStore().flush();
            blockStore.flush();
        }

        printInfo("consensus-replay ({}) visited {} blocks, {} mismatches", scenario, blocksVisited, mismatches);
        printInfo("invariant violations: {}", invariantViolations);
        if (scenario == Scenario.BACKTEST) {
            printInfo("backtest blast radius: {} diverging blocks, {} of them P1 violations (legacy-only)",
                    divergentBlocks, p1Violations);
        }
        printInfo("phase totals: fetch {} s, execute {} s, digest+write {} s",
                fetchNanos / 1_000_000_000L, executeNanos / 1_000_000_000L, digestNanos / 1_000_000_000L);

        return mismatches == 0 ? 0 : 1;
    }

    /** Why this invocation must not run, or null: refusing beats thousands of false divergences. */
    private String refusalReason(ActivationConfig activationConfig) {
        if (scenario != Scenario.BACKTEST) {
            if (ruleKey != null || forceAt != null) {
                return "--rule and --force-at only apply to --scenario BACKTEST; without it the run is a plain "
                        + scenario + " and would look green for the wrong reason";
            }

            return null;
        }

        if (ruleKey == null || forceAt == null) {
            return "BACKTEST needs --rule <consensusRuleConfigKey> and --force-at <height>";
        }

        if (saveState) {
            return "BACKTEST must not persist state: forced-activation tries would corrupt the snapshot for later runs";
        }

        try {
            forcedRule = ConsensusRule.fromConfigKey(ruleKey);
        } catch (IllegalArgumentException e) {
            return "unknown consensus rule '" + ruleKey + "'";
        }

        if (NOT_BACKTESTABLE.contains(forcedRule)) {
            return ruleKey + " is not backtestable: it changes header format or hashing "
                    + "(see ActivationConfig.getHeaderVersion), so every stored header would fail to match "
                    + "for reasons unrelated to the change under test";
        }

        if (!activationConfig.isActive(forcedRule, forceAt)) {
            return ruleKey + " is not active at height " + forceAt + " in the loaded config; "
                    + "pass -Xblockchain.config.consensusRules." + ruleKey + "=" + forceAt;
        }

        return null;
    }

    private Writer openWriter() throws IOException {
        if (reportPath == null) {
            return new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        }

        if (resume) {
            return Files.newBufferedWriter(Paths.get(reportPath), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }

        return Files.newBufferedWriter(Paths.get(reportPath), StandardCharsets.UTF_8);
    }

    /**
     * Finds where an interrupted run stopped and truncates its partial trailing record, so the
     * stream stays parseable and no block is replayed twice. Mismatches already recorded are
     * counted back in, so the exit code still describes the whole range.
     */
    private long resumeFrom() throws IOException {
        if (reportPath == null) {
            throw new IllegalArgumentException("--resume needs --report: there is nothing to resume from on stdout");
        }

        Path path = Paths.get(reportPath);
        if (!Files.exists(path)) {
            printInfo("--resume: no report at {}, starting from block {}", reportPath, fromBlockNumber);
            return fromBlockNumber;
        }

        long completeBytes = 0;
        long lastNumber = -1;
        long recovered = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                long number = blockNumberOf(line);
                if (number < 0) {
                    // A partial trailing record: stop here and drop it.
                    break;
                }

                completeBytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
                lastNumber = number;
                if (isMismatch(line)) {
                    recovered++;
                }
            }
        }

        if (Files.size(path) != completeBytes) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.truncate(completeBytes);
            }
            printInfo("--resume: dropped a partial trailing record");
        }

        mismatches += recovered;
        long next = lastNumber < 0 ? fromBlockNumber : lastNumber + 1;
        printInfo("--resume: {} complete records, {} mismatches carried over, continuing at block {}",
                lastNumber < 0 ? 0 : lastNumber, recovered, next);

        return next;
    }

    /** Block number of a complete record, or -1 if the line is truncated or malformed. */
    private static long blockNumberOf(String line) {
        if (!line.endsWith("}")) {
            return -1;
        }

        int at = line.indexOf("\"number\":");
        if (at < 0) {
            return -1;
        }

        int start = at + 9;
        int end = start;
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }

        return end == start ? -1 : Long.parseLong(line.substring(start, end));
    }

    private static boolean isMismatch(String line) {
        if (line.contains("\"kind\":\"invariant\"")) {
            return true;
        }

        // A BACKTEST record is only a failure when a legacy-only block diverged: everything else
        // there is the measured blast radius, which must not move the exit code.
        if (line.contains("\"scenario\":\"BACKTEST\"")) {
            return line.contains("\"p1Violation\":true");
        }

        return line.contains("\"fidelityOk\":false")
                || line.contains("\"executionFailed\":true")
                || line.contains("\"executionInterrupted\":true")
                || line.contains("\"blockLoadFailed\":true")
                || line.contains("\"missing\":true")
                || line.contains("\"parentMissing\":true");
    }

    private void replayRange(BlockExecutor blockExecutor, BlockStore blockStore,
                             StateRootHandler stateRootHandler, ActivationConfig activationConfig,
                             TrieStore trieStore, Writer writer) throws IOException {
        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block;
            Block parent;
            long fetchStart = System.nanoTime();
            try {
                // Constructing a Block validates its transactions trie root, so a block whose
                // transactions no longer re-encode faithfully throws here, before execution.
                block = blockStore.getChainBlockByNumber(n);
                if (block == null) {
                    writer.write(String.format("{\"kind\":\"block\",\"number\":%d,\"missing\":true}%n", n));
                    mismatches++;
                    continue;
                }

                parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
                if (parent == null) {
                    writer.write(String.format("{\"kind\":\"block\",\"number\":%d,\"parentMissing\":true}%n", n));
                    mismatches++;
                    continue;
                }
            } catch (RuntimeException e) {
                mismatches++;
                writer.write(String.format("{\"kind\":\"block\",\"number\":%d,\"blockLoadFailed\":true,%s}%n",
                        n, DigestJson.errorFields(e)));
                blocksVisited++;
                continue;
            } finally {
                fetchNanos += System.nanoTime() - fetchStart;
            }

            replayBlock(blockExecutor, stateRootHandler, activationConfig, trieStore, block, parent, writer);

            blocksVisited++;
            if (progressEvery > 0 && blocksVisited % progressEvery == 0) {
                printInfo("consensus-replay at block {} ({} mismatches) | fetch {}s execute {}s digest+write {}s",
                        n, mismatches,
                        fetchNanos / 1_000_000_000L, executeNanos / 1_000_000_000L, digestNanos / 1_000_000_000L);
            }
        }
    }

    private void replayBlock(BlockExecutor blockExecutor, StateRootHandler stateRootHandler,
                             ActivationConfig activationConfig, TrieStore trieStore, Block block, Block parent,
                             Writer writer) throws IOException {
        long startedAt = System.nanoTime();

        BlockResult result;
        try {
            result = blockExecutor.execute(null, 0, block, parent.getHeader(), false, false, saveState);
        } catch (RuntimeException e) {
            executeNanos += System.nanoTime() - startedAt;
            writer.write(noResult(block, String.format("\"executionFailed\":true,%s", DigestJson.errorFields(e)),
                    micros(startedAt)));
            return;
        }

        executeNanos += System.nanoTime() - startedAt;

        if (result.getFinalState() == null) {
            // BlockExecutor reports an interrupted or collided execution by returning a result
            // with no final state. Under BACKTEST that is a likely outcome, not a reason to abort.
            writer.write(noResult(block, "\"executionInterrupted\":true", micros(startedAt)));
            return;
        }

        String extra = scenario == Scenario.REPEAT
                ? repeat(blockExecutor, activationConfig, block, parent, result)
                : "";

        // Measure the digest from after the repeat pass: repeat() books its own execution time
        // to executeNanos, and spanning it here would count that work in both phases.
        long digestFrom = System.nanoTime();

        writer.write(invariants(activationConfig, trieStore, block, result));
        writer.write(digest(stateRootHandler, activationConfig, block, result, micros(startedAt), extra));

        digestNanos += System.nanoTime() - digestFrom;
    }

    /** Record for a block with no usable result; under BACKTEST that is a divergence like any other. */
    private String noResult(Block block, String fields, long micros) {
        StringBuilder sb = new StringBuilder(320);
        sb.append(String.format("{\"kind\":\"block\",\"number\":%d,\"scenario\":\"%s\",%s",
                block.getNumber(), scenario, fields));

        if (scenario == Scenario.BACKTEST) {
            sb.append(backtestClassification(block, true, "[\"noResult\"]"));
        } else {
            mismatches++;
        }

        sb.append(String.format(",\"micros\":%d}%n", micros));

        return sb.toString();
    }

    private String digest(StateRootHandler stateRootHandler, ActivationConfig activationConfig,
                          Block block, BlockResult result, long micros, String extra) {
        BlockHeader header = block.getHeader();
        long number = header.getNumber();
        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, number);

        byte[] stateRoot = result.getFinalState().getHash().getBytes();
        byte[] receiptsRoot = BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(), isRskip126Enabled);
        byte[] logsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());

        StringBuilder sb = new StringBuilder(512);
        sb.append(String.format("{\"kind\":\"block\",\"number\":%d,\"scenario\":\"%s\"", number, scenario));
        sb.append(consensusFields(header, result, isRskip126Enabled));
        sb.append(String.format(",\"micros\":%d", micros));

        if (scenario == Scenario.FIDELITY) {
            Comparison comparison = compareToHeader(stateRootHandler, block, result, stateRoot, receiptsRoot, logsBloom, "fidelityOk");
            if (!comparison.ok) {
                mismatches++;
            }
            sb.append(comparison.json);
        } else if (scenario == Scenario.BACKTEST) {
            Comparison comparison = compareToHeader(stateRootHandler, block, result, stateRoot, receiptsRoot, logsBloom, "matchesStoredHeader");
            sb.append(comparison.json);
            sb.append(backtestClassification(block, !comparison.ok, comparison.divergedFields));
        }

        sb.append(extra);
        sb.append(String.format("}%n"));

        return sb.toString();
    }

    /** Divergence is measured, not failed; a diverging legacy-only block is the P1 violation. */
    private String backtestClassification(Block block, boolean diverged, String divergedFields) {
        boolean legacyOnly = isLegacyOnly(block);
        boolean p1Violation = diverged && legacyOnly;

        if (diverged) {
            divergentBlocks++;
        }

        if (p1Violation) {
            p1Violations++;
            mismatches++;
        }

        return String.format(",\"forcedRule\":\"%s\",\"forcedAt\":%d,\"ruleActiveHere\":%b"
                        + ",\"legacyOnly\":%b,\"diverged\":%b,\"divergedFields\":%s,\"p1Violation\":%b",
                ruleKey, forceAt, block.getNumber() >= forceAt, legacyOnly, diverged, divergedFields, p1Violation);
    }

    /** True when no transaction in the block is of a type the forced rule introduces. */
    private boolean isLegacyOnly(Block block) {
        Set<TransactionType> introduced = TYPES_INTRODUCED.getOrDefault(forcedRule, EnumSet.noneOf(TransactionType.class));

        return block.getTransactionsList().stream().noneMatch(tx -> introduced.contains(tx.getType()));
    }

    /**
     * Executes the block a second time and requires the consensus fields to agree.
     *
     * Re-execution is safe by design: {@code BlockExecutor.execute} starts from a fresh
     * {@code repositoryLocator.startTrackingAt(parent)} each call, which is why a block can be
     * executed once for mining and again to connect it.
     *
     * A disagreement here outranks whatever change prompted the run: if replay is not repeatable,
     * no divergence found by any other scenario can be attributed to anything.
     */
    private String repeat(BlockExecutor blockExecutor, ActivationConfig activationConfig,
                          Block block, Block parent, BlockResult first) {
        boolean isRskip126Enabled = activationConfig.isActive(ConsensusRule.RSKIP126, block.getNumber());
        String firstFields = consensusFields(block.getHeader(), first, isRskip126Enabled);

        long startedAt = System.nanoTime();
        BlockResult second;
        try {
            second = blockExecutor.execute(null, 0, block, parent.getHeader(), false, false, saveState);
        } catch (RuntimeException e) {
            mismatches++;
            executeNanos += System.nanoTime() - startedAt;
            return String.format(",\"repeatOk\":false,\"secondRunFailed\":true,%s", DigestJson.errorFields(e));
        }
        executeNanos += System.nanoTime() - startedAt;

        if (second.getFinalState() == null) {
            mismatches++;
            return ",\"repeatOk\":false,\"secondRunInterrupted\":true";
        }

        String secondFields = consensusFields(block.getHeader(), second, isRskip126Enabled);
        if (firstFields.equals(secondFields)) {
            return ",\"repeatOk\":true";
        }

        mismatches++;

        return String.format(",\"repeatOk\":false,\"secondRun\":\"%s\"",
                secondFields.replace("\"", "\\\""));
    }

    /**
     * The fields that must not vary between two executions of the same block. Naming them in one
     * place is what makes determinism testable: {@code REPEAT} compares exactly this string, and
     * everything outside it — wall time, and the fidelity verdict — is excluded by construction.
     *
     * {@code txEdges} is here because it carries the RSKIP-144 parallel schedule, which the roots
     * alone would not reveal.
     */
    private static String consensusFields(BlockHeader header, BlockResult result, boolean isRskip126Enabled) {
        byte[] stateRoot = result.getFinalState().getHash().getBytes();
        byte[] receiptsRoot = BlockHashesHelper.calculateReceiptsTrieRoot(result.getTransactionReceipts(), isRskip126Enabled);
        byte[] logsBloom = BlockExecutor.calculateLogsBloom(result.getTransactionReceipts());

        StringBuilder sb = new StringBuilder(384);
        sb.append(String.format(",\"stateRoot\":\"%s\"", ByteUtil.toHexString(stateRoot)));
        sb.append(String.format(",\"txTrieRoot\":\"%s\"", ByteUtil.toHexString(header.getTxTrieRoot())));
        sb.append(String.format(",\"receiptsRoot\":\"%s\"", ByteUtil.toHexString(receiptsRoot)));
        // The bloom is 256 bytes; a hash keeps the stream diffable without 512 hex chars per block.
        sb.append(String.format(",\"logsBloomHash\":\"%s\"", ByteUtil.toHexString(HashUtil.keccak256(logsBloom))));
        sb.append(String.format(",\"gasUsed\":%d", result.getGasUsed()));
        sb.append(String.format(",\"paidFees\":\"%s\"", result.getPaidFees().asBigInteger()));
        sb.append(String.format(",\"executedTxs\":%d", result.getExecutedTransactions().size()));
        sb.append(String.format(",\"invalidTxs\":%d", result.getInvalidTransactions().size()));
        sb.append(String.format(",\"txEdges\":%s", edges(result.getTxEdges())));

        return sb.toString();
    }

    /** The verdict of comparing one block against its stored header, and the fields that differ. */
    private static final class Comparison {
        private final boolean ok;
        private final String divergedFields;
        private final String json;

        private Comparison(boolean ok, String divergedFields, String json) {
            this.ok = ok;
            this.divergedFields = divergedFields;
            this.json = json;
        }
    }

    private Comparison compareToHeader(StateRootHandler stateRootHandler, Block block, BlockResult result,
                                       byte[] stateRoot, byte[] receiptsRoot, byte[] logsBloom, String verdictKey) {
        BlockHeader header = block.getHeader();

        String storedStateRoot;
        boolean stateRootOk;
        try {
            Keccak256 translated = stateRootHandler.translate(header);
            storedStateRoot = translated.toHexString();
            stateRootOk = Arrays.equals(stateRoot, translated.getBytes());
        } catch (RuntimeException e) {
            // A pre-RSKIP126 block needs the translation store; record rather than abort the run.
            storedStateRoot = null;
            stateRootOk = false;
        }

        boolean receiptsRootOk = Arrays.equals(receiptsRoot, header.getReceiptsRoot());
        boolean logsBloomOk = Arrays.equals(logsBloom, header.getLogsBloom());
        boolean gasUsedOk = result.getGasUsed() == header.getGasUsed();
        boolean paidFeesOk = result.getPaidFees().equals(header.getPaidFees());
        boolean txListOk = result.getExecutedTransactions().equals(block.getTransactionsList());
        boolean ok = stateRootOk && receiptsRootOk && logsBloomOk && gasUsedOk && paidFeesOk && txListOk;

        List<String> diverged = new ArrayList<>();
        addIfDiverged(diverged, "stateRoot", stateRootOk);
        addIfDiverged(diverged, "receiptsRoot", receiptsRootOk);
        addIfDiverged(diverged, "logsBloom", logsBloomOk);
        addIfDiverged(diverged, "gasUsed", gasUsedOk);
        addIfDiverged(diverged, "paidFees", paidFeesOk);
        addIfDiverged(diverged, "txList", txListOk);

        StringBuilder sb = new StringBuilder(256);
        sb.append(String.format(",\"%s\":%b", verdictKey, ok));
        sb.append(String.format(",\"stateRootOk\":%b,\"receiptsRootOk\":%b,\"logsBloomOk\":%b", stateRootOk, receiptsRootOk, logsBloomOk));
        sb.append(String.format(",\"gasUsedOk\":%b,\"paidFeesOk\":%b,\"txListOk\":%b", gasUsedOk, paidFeesOk, txListOk));

        if (!ok) {
            sb.append(String.format(",\"storedStateRoot\":%s", DigestJson.quoteOrNull(storedStateRoot)));
            sb.append(String.format(",\"storedReceiptsRoot\":\"%s\"", ByteUtil.toHexString(header.getReceiptsRoot())));
            sb.append(String.format(",\"storedGasUsed\":%d", header.getGasUsed()));
            sb.append(String.format(",\"storedPaidFees\":\"%s\"", header.getPaidFees().asBigInteger()));
            sb.append(String.format(",\"logsBloom\":\"%s\"", ByteUtil.toHexString(logsBloom)));
            sb.append(String.format(",\"storedLogsBloom\":\"%s\"", ByteUtil.toHexString(header.getLogsBloom())));
            sb.append(String.format(",\"invalidTxHashes\":%s", txHashes(result.getInvalidTransactions())));
        }

        String fields = diverged.stream().map(f -> "\"" + f + "\"").collect(Collectors.joining(",", "[", "]"));

        return new Comparison(ok, fields, sb.toString());
    }

    private static void addIfDiverged(List<String> diverged, String field, boolean fieldOk) {
        if (!fieldOk) {
            diverged.add(field);
        }
    }

    /** Invariants that hold on every block whatever the scenario; see ticket 11 for what is left out. */
    private String invariants(ActivationConfig activationConfig, TrieStore trieStore, Block block, BlockResult result) {
        long number = block.getNumber();
        List<TransactionReceipt> receipts = result.getTransactionReceipts();
        StringBuilder sb = new StringBuilder();

        BigInteger expectedFees = BigInteger.ZERO;
        BigInteger receiptGasSum = BigInteger.ZERO;
        for (TransactionReceipt receipt : receipts) {
            BigInteger gasUsed = unsigned(receipt.getGasUsed());
            receiptGasSum = receiptGasSum.add(gasUsed);
            expectedFees = expectedFees.add(receipt.getTransaction().getGasPrice().asBigInteger().multiply(gasUsed));
        }

        BigInteger paidFees = result.getPaidFees().asBigInteger();
        if (!paidFees.equals(expectedFees)) {
            sb.append(violation(number, "fees", paidFees.toString(), expectedFees.toString()));
        }

        BigInteger blockGasUsed = BigInteger.valueOf(result.getGasUsed());
        if (!blockGasUsed.equals(receiptGasSum)) {
            sb.append(violation(number, "gas_receipt_sum", blockGasUsed.toString(), receiptGasSum.toString()));
        }

        // Only pre-RSKIP144 is the last receipt's cumulative gas the block total: with parallel
        // execution it accumulates per sublist, so the two are no longer the same quantity.
        if (!receipts.isEmpty() && !activationConfig.isActive(ConsensusRule.RSKIP144, number)) {
            long cumulative = receipts.get(receipts.size() - 1).getCumulativeGasLong();
            if (cumulative != result.getGasUsed()) {
                sb.append(violation(number, "gas_cumulative", Long.toString(result.getGasUsed()), Long.toString(cumulative)));
            }
        }

        long gasLimit = ByteUtil.byteArrayToLong(block.getHeader().getGasLimit());
        if (result.getGasUsed() > gasLimit) {
            sb.append(violation(number, "gas_bounds", Long.toString(result.getGasUsed()), "<=" + gasLimit));
        }

        sb.append(nonceMonotonicity(number, receipts));
        sb.append(stateShape(trieStore, number, receipts, result));

        return sb.toString();
    }

    /** A sender's nonce must advance across the range; a repeat or a rollback is a replay bug. */
    private String nonceMonotonicity(long number, List<TransactionReceipt> receipts) {
        StringBuilder sb = new StringBuilder();

        for (TransactionReceipt receipt : receipts) {
            Transaction tx = receipt.getTransaction();
            RskAddress sender = senderOf(tx);
            if (sender == null) {
                continue;
            }

            BigInteger nonce = unsigned(tx.getNonce());
            BigInteger previous = lastNonce.put(sender, nonce);
            if (previous != null && nonce.compareTo(previous) <= 0) {
                sb.append(violation(number, "nonce_monotonic",
                        sender + "=" + nonce, "> " + previous));
                lastNonce.put(sender, previous.max(nonce));
            }
        }

        return sb.toString();
    }

    /** Delegation-prefixed code must be exactly 23 bytes, probed on the accounts this block could
     * have touched; a whole-trie sweep is a separate state scan (P5). */
    private String stateShape(TrieStore trieStore, long number, List<TransactionReceipt> receipts, BlockResult result) {
        Set<RskAddress> addresses = new LinkedHashSet<>();
        for (TransactionReceipt receipt : receipts) {
            Transaction tx = receipt.getTransaction();
            RskAddress sender = senderOf(tx);
            if (sender != null) {
                addresses.add(sender);
            }
            if (tx.getReceiveAddress() != null) {
                addresses.add(tx.getReceiveAddress());
            }
            List<SetCodeAuthorization> authorizations = tx.getAuthorizationList();
            if (authorizations != null) {
                for (SetCodeAuthorization authorization : authorizations) {
                    addresses.add(authorization.getAddress());
                }
            }
        }

        if (addresses.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, result.getFinalState()));
        for (RskAddress address : addresses) {
            byte[] code = repository.getCode(address);
            if (hasDelegationPrefix(code) && code.length != DELEGATION_CODE_SIZE) {
                sb.append(violation(number, "state_shape",
                        address + " code length " + code.length, Integer.toString(DELEGATION_CODE_SIZE)));
            }
        }

        return sb.toString();
    }

    private static final int DELEGATION_CODE_SIZE = DelegationCodeResolver.DELEGATION_PREFIX.length + 20;

    private static boolean hasDelegationPrefix(byte[] code) {
        byte[] prefix = DelegationCodeResolver.DELEGATION_PREFIX;

        return code != null && code.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(code, prefix.length), prefix);
    }

    private String violation(long number, String invariant, String observed, String expected) {
        invariantViolations++;
        mismatches++;

        return String.format("{\"kind\":\"invariant\",\"number\":%d,\"scenario\":\"%s\",\"invariant\":\"%s\",\"observed\":%s,\"expected\":%s}%n",
                number, scenario, invariant, DigestJson.quoteOrNull(observed), DigestJson.quoteOrNull(expected));
    }

    /** Null for a transaction with no signature: the REMASC transaction has no recoverable sender. */
    private static RskAddress senderOf(Transaction tx) {
        return tx.getSignature() == null ? null : tx.getSender();
    }

    private static BigInteger unsigned(byte[] value) {
        return value == null || value.length == 0 ? BigInteger.ZERO : new BigInteger(1, value);
    }

    private static long micros(long startedAt) {
        return (System.nanoTime() - startedAt) / 1000L;
    }

    private static String edges(short[] txEdges) {
        if (txEdges == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < txEdges.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(txEdges[i]);
        }

        return sb.append(']').toString();
    }

    private static String txHashes(List<Transaction> transactions) {
        return transactions.stream()
                .map(tx -> "\"" + tx.getHash().toHexString() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
