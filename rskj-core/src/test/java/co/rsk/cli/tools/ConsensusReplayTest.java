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

import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.MutableTrieImpl;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import co.rsk.util.NodeStopper;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import co.rsk.core.bc.BlockResult;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.datasource.DbKind;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ConsensusReplayTest {

    @TempDir
    private Path tempDir;

    @Test
    void fidelityOverCleanChainReportsNoMismatches() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("fidelity.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size(), "one record per replayed block: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"fidelityOk\":true")),
                "every block must match the stored header: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"stateRoot\":\"")),
                "the digest must carry the computed state root: " + lines);
    }

    /**
     * Sensitivity probe: proves the fidelity comparison is load-bearing. A clean run is only
     * evidence if a tampered header is actually caught.
     */
    @Test
    void tamperedStoredReceiptsRootIsDetected() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("tampered.jsonl");

        Block real = world.getBlockStore().getChainBlockByNumber(1);
        BlockHeader header = spy(real.getHeader());
        doReturn(new byte[32]).when(header).getReceiptsRoot();

        Block tampered = spy(real);
        doReturn(header).when(tampered).getHeader();

        NodeStopper stopper = run(world, blockStoreReturning(world, tampered), world.getBlockExecutor(), report, "FIDELITY");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"receiptsRootOk\":false")),
                "a tampered stored receipts root must be reported: " + lines);
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"fidelityOk\":false")),
                "the block must be marked as diverging: " + lines);
    }

    @Test
    void digestScenarioEmitsNoComparison() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("digest.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "DIGEST");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertFalse(lines.isEmpty());
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("fidelityOk")),
                "digest mode compares nothing; it only emits a stream to diff: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"receiptsRoot\":\"")),
                "digest mode must still emit the computed roots: " + lines);
    }

    /**
     * A block that fails to execute must be recorded and the run must continue: execution is
     * anchored per block, so one failure says nothing about the next.
     */
    @Test
    void executionFailureIsRecordedAndDoesNotAbortTheRun() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("failure.jsonl");

        BlockExecutor failing = mock(BlockExecutor.class);
        doThrow(new IllegalStateException("boom"))
                .when(failing).execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        NodeStopper stopper = run(world, world.getBlockStore(), failing, report, "FIDELITY");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size(), "the run must continue past a failing block: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"executionFailed\":true")), lines.toString());
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("java.lang.IllegalStateException")), lines.toString());
    }

    /**
     * A block that cannot even be constructed — its transactions no longer re-encode to the
     * stored trie root — must be recorded and the range must continue.
     */
    @Test
    void blockLoadFailureIsRecordedAndDoesNotAbortTheRun() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("load-failure.jsonl");

        BlockStore blockStore = spy(world.getBlockStore());
        doThrow(new IllegalArgumentException("Transactions trie root validation failed for block 1"))
                .when(blockStore).getChainBlockByNumber(1L);

        NodeStopper stopper = run(world, blockStore, world.getBlockExecutor(), report, "FIDELITY");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size(), "the run must continue past the failing block: " + lines);
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"blockLoadFailed\":true")), lines.toString());
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"fidelityOk\":true")),
                "the following block must still be replayed normally: " + lines);
    }

    /**
     * A killed run leaves a partial trailing record. Resuming must drop it, so the stream stays
     * parseable, and must not replay the block that already completed.
     */
    @Test
    void resumeDropsThePartialRecordAndContinues() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("resume.jsonl");

        NodeStopper first = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY");
        verify(first).stop(0);

        // Simulate a kill mid-write: keep block 1's record, leave block 2's truncated.
        List<String> complete = Files.readAllLines(report, StandardCharsets.UTF_8);
        Files.writeString(report, complete.get(0) + System.lineSeparator()
                + "{\"kind\":\"block\",\"number\":2,\"scenario\":\"FIDELITY\",\"stateRoot\":\"dead", StandardCharsets.UTF_8);

        NodeStopper second = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY", "--resume");
        verify(second).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size(), "one complete record per block, no partial: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.endsWith("}")), "no truncated record may survive: " + lines);
        Assertions.assertEquals(withoutMicros(complete), withoutMicros(lines),
                "resuming must reproduce the uninterrupted stream (micros excluded: it is wall time)");
    }

    /**
     * The exit code has to describe the whole range, not just the resumed tail, so divergences
     * already recorded before the interruption must still count.
     */
    @Test
    void resumeCarriesOverMismatchesAlreadyRecorded() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("resume-mismatch.jsonl");

        Files.writeString(report,
                "{\"kind\":\"block\",\"number\":1,\"blockLoadFailed\":true,\"errorType\":\"x\"}" + System.lineSeparator(),
                StandardCharsets.UTF_8);

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY", "--resume");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        Assertions.assertEquals(2, lines.size(), "only the unfinished block may be replayed: " + lines);
        Assertions.assertTrue(lines.get(1).contains("\"number\":2"), "must continue after the recorded block: " + lines);
    }

    @Test
    void resumeWithNoExistingReportStartsFromTheBeginning() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("resume-fresh.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY", "--resume");

        verify(stopper).stop(0);
        Assertions.assertEquals(2, Files.readAllLines(report, StandardCharsets.UTF_8).size());
    }

    @Test
    void repeatOverACleanChainAgrees() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("repeat.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "REPEAT");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size());
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"repeatOk\":true")),
                "executing the same block twice must agree: " + lines);
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("fidelityOk")),
                "REPEAT compares runs against each other, not against the header: " + lines);
    }

    /**
     * Sensitivity probe, and a discrimination check: an executor whose results differ between
     * calls must be caught, and one whose results agree must not be. Without this, a green
     * REPEAT run would prove only that the comparison never fires.
     */
    @Test
    void repeatDetectsAnExecutorThatDisagreesWithItself() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("repeat-flaky.jsonl");

        Block block1 = world.getBlockStore().getChainBlockByNumber(1);
        Block block2 = world.getBlockStore().getChainBlockByNumber(2);
        BlockResult first = executeOnce(world, block1);
        BlockResult second = executeOnce(world, block2);

        // Block 1 sees two different results; block 2 sees the same result twice.
        BlockExecutor flaky = mock(BlockExecutor.class);
        when(flaky.execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(first, second, second, second);

        NodeStopper stopper = run(world, world.getBlockStore(), flaky, report, "REPEAT");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.get(0).contains("\"repeatOk\":false"),
                "differing results must be reported: " + lines.get(0));
        Assertions.assertTrue(lines.get(0).contains("secondRun"),
                "the disagreeing fields must be recorded so the divergence can be read: " + lines.get(0));
        Assertions.assertTrue(lines.get(1).contains("\"repeatOk\":true"),
                "agreeing results must not be flagged: " + lines.get(1));
    }

    // ---------------------------------------------------------------------------------------
    // BACKTEST (ticket 08)
    // ---------------------------------------------------------------------------------------

    /**
     * A backtest stream has to be self-describing: without the forced rule and height in every
     * record, a divergence set cannot be attributed to the fork it was produced under.
     */
    @Test
    void backtestRecordsTheForcedRuleAndHeightInEveryDigest() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "BACKTEST",
                "--rule", "rskip545", "--force-at", "0");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size(), "one record per replayed block: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"forcedRule\":\"rskip545\"")
                        && l.contains("\"forcedAt\":0")),
                "every record must name the rule that was forced and from where: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"diverged\":false")
                        && l.contains("\"legacyOnly\":true") && l.contains("\"p1Violation\":false")),
                "an unchanged chain must show no divergence: " + lines);
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("fidelityOk")),
                "BACKTEST must not report divergence as a fidelity failure: " + lines);
    }

    /**
     * Sensitivity probe for P1: a block whose transactions are all of pre-existing types must not
     * diverge, so when one does the run has to go red and say in which field.
     */
    @Test
    void backtestFlagsADivergingLegacyOnlyBlockAsAP1Violation() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest-p1.jsonl");

        Block real = world.getBlockStore().getChainBlockByNumber(1);
        BlockHeader header = spy(real.getHeader());
        doReturn(new byte[32]).when(header).getReceiptsRoot();

        Block tampered = spy(real);
        doReturn(header).when(tampered).getHeader();

        NodeStopper stopper = run(world, blockStoreReturning(world, tampered), world.getBlockExecutor(), report,
                "BACKTEST", "--rule", "rskip545", "--force-at", "0");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.get(0).contains("\"p1Violation\":true"),
                "a legacy-only block that diverges is a P1 violation: " + lines.get(0));
        Assertions.assertTrue(lines.get(0).contains("\"divergedFields\":[\"receiptsRoot\"]"),
                "the divergence set must name the field: " + lines.get(0));
        Assertions.assertTrue(lines.get(1).contains("\"diverged\":false"),
                "the clean block must not be flagged: " + lines.get(1));
    }

    /**
     * Discrimination check: under BACKTEST divergence is the expected output, so a diverging block
     * that carries a transaction of the type the forced rule introduces is measured, not failed.
     * Without this, the P1 probe above would only prove that everything is reported as a failure.
     */
    @Test
    void backtestDoesNotFailADivergingBlockThatCarriesANewTransactionType() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest-type4.jsonl");

        Block real = world.getBlockStore().getChainBlockByNumber(1);
        BlockHeader header = spy(real.getHeader());
        doReturn(new byte[32]).when(header).getReceiptsRoot();

        List<Transaction> transactions = new ArrayList<>(real.getTransactionsList());
        Transaction type4 = spy(transactions.get(0));
        doReturn(TransactionType.TYPE_4).when(type4).getType();
        transactions.set(0, type4);

        Block tampered = spy(real);
        doReturn(header).when(tampered).getHeader();
        doReturn(transactions).when(tampered).getTransactionsList();

        NodeStopper stopper = run(world, blockStoreReturning(world, tampered), world.getBlockExecutor(), report,
                "BACKTEST", "--rule", "rskip545", "--force-at", "0");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.get(0).contains("\"diverged\":true") && lines.get(0).contains("\"legacyOnly\":false"),
                "the divergence must still be measured: " + lines.get(0));
        Assertions.assertTrue(lines.get(0).contains("\"p1Violation\":false"),
                "a block carrying the new type is expected to diverge: " + lines.get(0));
    }

    /**
     * Forcing a rule that changes header format or hashing makes every stored header invalid for
     * reasons unrelated to the change under test, so the run must be refused, not reported.
     */
    @Test
    void backtestRefusesRulesThatChangeTheHeaderFormat() throws FileNotFoundException, DslProcessorException, IOException {
        for (String rule : List.of("rskip351", "rskip535")) {
            World world = buildWorld();
            Path report = tempDir.resolve("backtest-refuse-" + rule + ".jsonl");

            NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "BACKTEST",
                    "--rule", rule, "--force-at", "0");

            verify(stopper).stop(1);

            List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

            Assertions.assertEquals(1, lines.size(), "a refused run must emit no block records: " + lines);
            Assertions.assertTrue(lines.get(0).contains("\"kind\":\"refusal\"")
                            && lines.get(0).contains("not backtestable"),
                    "the refusal must say why: " + lines.get(0));
        }
    }

    /**
     * Discrimination check for the exclusion set: a rule that is backtestable must not be refused.
     * Covered by {@link #backtestRecordsTheForcedRuleAndHeightInEveryDigest}, asserted here on the
     * absence of a refusal record so the exclusion list cannot silently grow to everything.
     */
    @Test
    void backtestAcceptsARuleOutsideTheExclusionSet() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest-accepted.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "BACKTEST",
                "--rule", "rskip144", "--force-at", "1");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("refusal")), lines.toString());
        Assertions.assertEquals(2, lines.size(), lines.toString());
    }

    @Test
    void backtestRefusesToPersistState() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest-save-state.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "BACKTEST",
                "--rule", "rskip545", "--force-at", "0", "--save-state");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains("must not persist state"),
                "a forced-activation run must never write into the snapshot: " + lines.get(0));
    }

    /**
     * The activation override lives in {@code -X}, so a BACKTEST whose rule is not actually active
     * is a plain fidelity run wearing a backtest label. It must be refused rather than look green.
     */
    @Test
    void backtestRefusesWhenTheRuleIsNotActuallyForcedOn() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest-not-forced.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "BACKTEST",
                "--rule", "rskip97", "--force-at", "1");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains("-Xblockchain.config.consensusRules.rskip97=1"),
                "the refusal must name the override the operator forgot: " + lines.get(0));
    }

    @Test
    void backtestRefusesWithoutARuleAndHeight() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("backtest-no-rule.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "BACKTEST");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains("--rule") && lines.get(0).contains("--force-at"), lines.get(0));
    }

    @Test
    void ruleOptionsOutsideBacktestAreRefused() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("rule-outside-backtest.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY",
                "--rule", "rskip545", "--force-at", "0");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains("only apply to --scenario BACKTEST"), lines.get(0));
    }

    // ---------------------------------------------------------------------------------------
    // Per-block invariants (ticket 11)
    // ---------------------------------------------------------------------------------------

    @Test
    void invariantsAreSilentOnACleanChain() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("invariants-clean.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(), world.getBlockExecutor(), report, "DIGEST");

        verify(stopper).stop(0);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("\"kind\":\"invariant\"")),
                "no invariant may fire on an unmodified chain: " + lines);
    }

    /**
     * Sensitivity probe: block paidFees must equal the sum over transactions of
     * gasUsed x effectiveGasPrice, so a block claiming a different total has to be localized.
     */
    @Test
    void feeAccountingViolationIsDetectedAndLocalized() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("invariant-fees.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(),
                executorReturning(world, 1, result -> doReturn(Coin.valueOf(12345L)).when(result).getPaidFees()),
                report, "DIGEST");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"invariant\":\"fees\"")
                        && l.contains("\"number\":1") && l.contains("\"observed\":\"12345\"")),
                "the fee violation must name the block and the observed total: " + lines);
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("\"invariant\":\"fees\"") && l.contains("\"number\":2")),
                "the untouched block must stay clean: " + lines);
    }

    /**
     * Before RSKIP-560 a failed precompile call is charged the full limit while its receipt reports
     * less gas, so fees cannot be derived from receipts and a mismatch there is valid history.
     */
    @Test
    void feeInvariantIsNotEvaluatedBeforeRskip692() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("invariant-fees-pre692.jsonl");
        ActivationConfig pre692 = spy(world.getConfig().getActivationConfig());
        doReturn(false).when(pre692).isActive(eq(ConsensusRule.RSKIP692), anyLong());

        NodeStopper stopper = run(new ConsensusReplay(), pre692, world, world.getBlockStore(),
                executorReturning(world, 1, result -> doReturn(Coin.valueOf(12345L)).when(result).getPaidFees()),
                report, "DIGEST");

        verify(stopper).stop(0);
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("\"invariant\":\"fees\"")),
                "fees must not be judged where the protocol does not guarantee them: " + lines);
    }

    /** Sensitivity probe: a block may not consume more gas than its own header allows. */
    @Test
    void gasBoundsViolationIsDetected() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("invariant-gas.jsonl");

        NodeStopper stopper = run(world, world.getBlockStore(),
                executorReturning(world, 1, result -> doReturn(Long.MAX_VALUE).when(result).getGasUsed()),
                report, "DIGEST");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"invariant\":\"gas_bounds\"")),
                "gasUsed above the block gas limit must be reported: " + lines);
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"invariant\":\"gas_receipt_sum\"")),
                "a block total that no longer matches its receipts must be reported: " + lines);
    }

    /**
     * Sensitivity probe: replaying block 1's result for block 2 replays its nonces, which is
     * exactly the shape of a nonce that fails to advance.
     */
    @Test
    void nonceMonotonicityViolationIsDetectedAcrossBlocks() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("invariant-nonce.jsonl");

        BlockResult first = executeOnce(world, world.getBlockStore().getChainBlockByNumber(1));
        Assertions.assertFalse(first.getTransactionReceipts().isEmpty(),
                "the probe needs a block with transactions, otherwise it proves nothing");

        BlockExecutor replaying = mock(BlockExecutor.class);
        when(replaying.execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(first);

        NodeStopper stopper = run(world, world.getBlockStore(), replaying, report, "DIGEST");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"invariant\":\"nonce_monotonic\"")
                        && l.contains("\"number\":2")),
                "a sender whose nonce does not advance must be reported, on the block that repeats it: " + lines);
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.contains("\"invariant\":\"nonce_monotonic\"")
                        && l.contains("\"number\":1")),
                "the first sighting of a nonce is not a violation: " + lines);
    }

    /**
     * Sensitivity probe for the state-shape invariant: code carrying the delegation prefix must be
     * exactly 23 bytes, so a truncated designator planted in the final state has to be caught.
     */
    @Test
    void malformedDelegationCodeIsDetected() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("invariant-state-shape.jsonl");

        BlockResult real = executeOnce(world, world.getBlockStore().getChainBlockByNumber(1));
        RskAddress sender = real.getTransactionReceipts().get(0).getTransaction().getSender();

        Repository repository = new MutableRepository(new MutableTrieImpl(world.getTrieStore(), real.getFinalState()));
        repository.saveCode(sender, new byte[] {(byte) 0xef, 0x01, 0x00, 0x11});
        Trie doctored = repository.getTrie();

        BlockResult planted = spy(real);
        doReturn(doctored).when(planted).getFinalState();

        BlockExecutor executor = mock(BlockExecutor.class);
        when(executor.execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(planted, executeOnce(world, world.getBlockStore().getChainBlockByNumber(2)));

        NodeStopper stopper = run(world, world.getBlockStore(), executor, report, "DIGEST");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"invariant\":\"state_shape\"")
                        && l.contains("code length 4") && l.contains("\"expected\":\"23\"")),
                "a delegation designator of the wrong length must be reported: " + lines);
    }

    /** An executor whose block result carries no final state must be recorded, not crash the run. */
    @Test
    void interruptedExecutionIsRecordedAndDoesNotAbortTheRun() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("interrupted.jsonl");

        BlockExecutor interrupted = mock(BlockExecutor.class);
        when(interrupted.execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT);

        NodeStopper stopper = run(world, world.getBlockStore(), interrupted, report, "FIDELITY");

        verify(stopper).stop(1);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertEquals(2, lines.size(), "the run must continue past an interrupted block: " + lines);
        Assertions.assertTrue(lines.stream().allMatch(l -> l.contains("\"executionInterrupted\":true")), lines.toString());
    }

    /**
     * Returns an executor that runs the real thing but hands back a doctored result for one block,
     * so an invariant can be shown to fire on that block and only that block.
     */
    private BlockExecutor executorReturning(World world, long number, Consumer<BlockResult> doctor) {
        BlockExecutor real = world.getBlockExecutor();
        BlockExecutor executor = mock(BlockExecutor.class);
        when(executor.execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Block block = invocation.getArgument(2);
                    BlockResult result = real.execute(null, 0, block, invocation.getArgument(3), false, false, false);
                    if (block.getNumber() != number) {
                        return result;
                    }

                    BlockResult doctored = spy(result);
                    doctor.accept(doctored);

                    return doctored;
                });

        return executor;
    }

    private static BlockResult executeOnce(World world, Block block) {
        Block parent = world.getBlockStore().getBlockByHash(block.getParentHash().getBytes());

        return world.getBlockExecutor().execute(null, 0, block, parent.getHeader(), false, false, false);
    }

    /** Drops the wall-time field, which varies between runs and must never be diffed. */
    private static List<String> withoutMicros(List<String> lines) {
        return lines.stream().map(l -> l.replaceAll(",\"micros\":\\d+", "")).toList();
    }

    private static World buildWorld() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        new WorldDslProcessor(world).processCommands(parser);

        return world;
    }

    private static Block tamperedReceiptsRoot(Block real) {
        BlockHeader header = spy(real.getHeader());
        doReturn(new byte[32]).when(header).getReceiptsRoot();
        Block tampered = spy(real);
        doReturn(header).when(tampered).getHeader();
        return tampered;
    }

    private BlockStore blockStoreReturning(World world, Block block) {
        long number = block.getNumber();
        BlockStore blockStore = spy(world.getBlockStore());
        doReturn(block).when(blockStore).getChainBlockByNumber(number);

        return blockStore;
    }

    // =========================================================================
    // Concurrent execution (--threads)
    // =========================================================================

    /** A threaded run must write exactly the records a sequential run writes; only timing may differ. */
    @Test
    void threadedRunWritesTheSameRecordsAsASequentialRun() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path sequential = tempDir.resolve("threads-1.jsonl");
        Path threaded = tempDir.resolve("threads-4.jsonl");

        verify(run(world, world.getBlockStore(), world.getBlockExecutor(), sequential, "FIDELITY")).stop(0);
        verify(run(world, world.getBlockStore(), world.getBlockExecutor(), threaded, "FIDELITY", "--threads", "4")).stop(0);

        Assertions.assertEquals(withoutTiming(sequential), withoutTiming(threaded));
    }

    /** Records are written in block order even when a later block finishes first. */
    @Test
    void threadedRunWritesRecordsInBlockOrderWhenAnEarlierBlockFinishesLast() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("threads-order.jsonl");
        BlockExecutor slowFirst = spy(world.getBlockExecutor());
        java.util.Map<Long, long[]> spans = new java.util.concurrent.ConcurrentHashMap<>();
        doAnswer(invocation -> {
            Block block = invocation.getArgument(2);
            long start = System.nanoTime();
            if (block.getNumber() == 1) {
                Thread.sleep(500);
            }
            Object result = invocation.callRealMethod();
            spans.put(block.getNumber(), new long[]{start, System.nanoTime()});
            return result;
        }).when(slowFirst).execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        verify(run(world, world.getBlockStore(), slowFirst, report, "FIDELITY", "--threads", "2")).stop(0);

        Assertions.assertTrue(spans.get(2L)[0] < spans.get(1L)[1],
                "block 2 must start before block 1 finishes, or nothing ran concurrently");
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        Assertions.assertTrue(lines.get(0).contains("\"number\":1,") && lines.get(1).contains("\"number\":2,"),
                "records must follow block order, not completion order: " + lines);
    }

    @Test
    void threadedRepeatStillDetectsAnExecutorThatDisagreesWithItself() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("threads-repeat.jsonl");
        BlockResult first = executeOnce(world, world.getBlockStore().getChainBlockByNumber(1));
        BlockResult second = executeOnce(world, world.getBlockStore().getChainBlockByNumber(2));
        BlockExecutor flaky = mock(BlockExecutor.class);
        doAnswer(invocation -> ((Block) invocation.getArgument(2)).getNumber() == 1 ? first : second)
                .doAnswer(invocation -> second)
                .when(flaky).execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        verify(run(world, world.getBlockStore(), flaky, report, "REPEAT", "--threads", "1")).stop(1);
        List<String> sequential = withoutTiming(report);

        Path threaded = tempDir.resolve("threads-repeat-4.jsonl");
        BlockExecutor flaky4 = mock(BlockExecutor.class);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.Map<Long, java.util.concurrent.atomic.AtomicInteger> perBlock = new java.util.concurrent.ConcurrentHashMap<>();
        doAnswer(invocation -> {
            long number = ((Block) invocation.getArgument(2)).getNumber();
            int nth = perBlock.computeIfAbsent(number, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            calls.incrementAndGet();
            return number == 1 && nth == 1 ? first : second;
        }).when(flaky4).execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        verify(run(world, world.getBlockStore(), flaky4, threaded, "REPEAT", "--threads", "4")).stop(1);
        Assertions.assertEquals(4, calls.get(), "each block must be executed exactly twice");
        Assertions.assertTrue(withoutTiming(threaded).get(0).contains("\"repeatOk\":false"), threaded.toString());
    }

    private static List<String> withoutTiming(Path report) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(report, StandardCharsets.UTF_8)) {
            out.add(line.replaceAll(",\"micros\":\\d+", ""));
        }
        return out;
    }

    // =========================================================================
    // Failure counts by record kind
    // =========================================================================

    /** An invariant violation is not a consensus divergence, and the counts must not let it read as one. */
    @Test
    void invariantViolationIsCountedApartFromDivergence() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        ConsensusReplay tool = new ConsensusReplay();

        NodeStopper stopper = run(tool, world, world.getBlockStore(),
                executorReturning(world, 1, result -> doReturn(Coin.valueOf(12345L)).when(result).getPaidFees()),
                tempDir.resolve("kinds-invariant.jsonl"), "DIGEST");

        verify(stopper).stop(1);
        Map<ConsensusReplay.Failure, Long> counts = tool.failureCounts();
        Assertions.assertEquals(0L, counts.get(ConsensusReplay.Failure.DIVERGED), counts::toString);
        Assertions.assertEquals(1L, counts.get(ConsensusReplay.Failure.INVARIANT), counts::toString);
        Assertions.assertEquals("0 diverged, 1 invariant violations, 0 not executed, 0 not loaded", tool.failureSummary());
    }

    @Test
    void fidelityDivergenceIsCountedAsDiverged() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        ConsensusReplay tool = new ConsensusReplay();
        Block tampered = tamperedReceiptsRoot(world.getBlockStore().getChainBlockByNumber(1));

        run(tool, world, blockStoreReturning(world, tampered), world.getBlockExecutor(),
                tempDir.resolve("kinds-diverged.jsonl"), "FIDELITY");

        Map<ConsensusReplay.Failure, Long> counts = tool.failureCounts();
        Assertions.assertEquals(1L, counts.get(ConsensusReplay.Failure.DIVERGED), counts::toString);
        Assertions.assertEquals(0L, counts.get(ConsensusReplay.Failure.INVARIANT), counts::toString);
    }

    @Test
    void executorFailureIsCountedAsNotExecuted() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        ConsensusReplay tool = new ConsensusReplay();
        BlockExecutor failing = mock(BlockExecutor.class);
        doThrow(new IllegalStateException("boom"))
                .when(failing).execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        run(tool, world, world.getBlockStore(), failing, tempDir.resolve("kinds-unexecuted.jsonl"), "FIDELITY");

        Map<ConsensusReplay.Failure, Long> counts = tool.failureCounts();
        Assertions.assertEquals(2L, counts.get(ConsensusReplay.Failure.NOT_EXECUTED), counts::toString);
        Assertions.assertEquals(0L, counts.get(ConsensusReplay.Failure.DIVERGED), counts::toString);
    }

    /** A resumed REPEAT run must still fail on a determinism divergence recorded before the interruption. */
    @Test
    void resumeCarriesOverARepeatDivergence() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("resume-repeat.jsonl");

        BlockResult first = executeOnce(world, world.getBlockStore().getChainBlockByNumber(1));
        BlockResult second = executeOnce(world, world.getBlockStore().getChainBlockByNumber(2));
        BlockExecutor flaky = mock(BlockExecutor.class);
        when(flaky.execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(first, second, second, second);
        run(world, world.getBlockStore(), flaky, report, "REPEAT");

        // Keep only block 1's record, as if the run had been killed after writing it.
        String block1 = Files.readAllLines(report, StandardCharsets.UTF_8).get(0);
        Assertions.assertTrue(block1.contains("\"repeatOk\":false"), block1);
        Files.writeString(report, block1 + System.lineSeparator(), StandardCharsets.UTF_8);

        ConsensusReplay resumed = new ConsensusReplay();
        NodeStopper stopper = run(resumed, world, world.getBlockStore(), world.getBlockExecutor(), report, "REPEAT", "--resume");

        verify(stopper).stop(1);
        Assertions.assertEquals(1L, resumed.failureCounts().get(ConsensusReplay.Failure.DIVERGED),
                resumed.failureCounts()::toString);
    }

    /**
     * A block's invariant records are written before its block record, so a run killed between the
     * two leaves an orphan invariant line. Resume must replay that block, not skip it.
     */
    @Test
    void resumeReplaysABlockWhoseInvariantRecordWasWrittenButNotItsBlockRecord() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("resume-orphan-invariant.jsonl");
        run(world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY");
        String block1 = Files.readAllLines(report, StandardCharsets.UTF_8).get(0);
        String orphan = "{\"kind\":\"invariant\",\"number\":2,\"scenario\":\"FIDELITY\",\"invariant\":\"fees\",\"observed\":\"1\",\"expected\":\"2\"}";
        Files.writeString(report, block1 + System.lineSeparator() + orphan + System.lineSeparator(), StandardCharsets.UTF_8);

        ConsensusReplay resumed = new ConsensusReplay();
        NodeStopper stopper = run(resumed, world, world.getBlockStore(), world.getBlockExecutor(), report, "FIDELITY", "--resume");

        verify(stopper).stop(0);
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        Assertions.assertEquals(2, lines.size(), "the orphan must be dropped and block 2 replayed: " + lines);
        Assertions.assertTrue(lines.get(1).contains("\"kind\":\"block\"") && lines.get(1).contains("\"number\":2"),
                "block 2 must have its own record: " + lines);
        Assertions.assertEquals(0L, resumed.failureCounts().get(ConsensusReplay.Failure.INVARIANT), resumed.failureSummary());
    }

    /**
     * Resume re-derives the counts from the stream, so for every kind it must agree with the counts
     * the live run kept. Resuming over a complete report replays nothing and leaves only the carry-over.
     */
    @Test
    void resumeRederivesTheSameCountsTheLiveRunKept() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Block tampered = tamperedReceiptsRoot(world.getBlockStore().getChainBlockByNumber(1));
        BlockExecutor throwing = mock(BlockExecutor.class);
        doThrow(new IllegalStateException("boom"))
                .when(throwing).execute(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        Object[][] runs = {
                {"diverged", blockStoreReturning(world, tampered), world.getBlockExecutor(), "FIDELITY"},
                {"invariant", world.getBlockStore(),
                        executorReturning(world, 1, result -> doReturn(Coin.valueOf(12345L)).when(result).getPaidFees()), "DIGEST"},
                {"unexecuted", world.getBlockStore(), throwing, "FIDELITY"},
        };

        for (Object[] r : runs) {
            Path report = tempDir.resolve("rederive-" + r[0] + ".jsonl");
            ConsensusReplay first = new ConsensusReplay();
            run(first, world, (BlockStore) r[1], (BlockExecutor) r[2], report, (String) r[3]);

            ConsensusReplay resumed = new ConsensusReplay();
            run(resumed, world, (BlockStore) r[1], (BlockExecutor) r[2], report, (String) r[3], "--resume");

            Assertions.assertEquals(first.failureCounts(), resumed.failureCounts(), (String) r[0]);
            Assertions.assertTrue(first.failureCounts().values().stream().anyMatch(c -> c > 0),
                    r[0] + " must actually produce a failure, or the comparison is vacuous");
        }
    }

    private NodeStopper run(World world, BlockStore blockStore, BlockExecutor blockExecutor,
                            Path report, String scenario, String... extra) {
        return run(new ConsensusReplay(), world, blockStore, blockExecutor, report, scenario, extra);
    }

    private NodeStopper run(ConsensusReplay tool, World world, BlockStore blockStore, BlockExecutor blockExecutor,
                            Path report, String scenario, String... extra) {
        return run(tool, world.getConfig().getActivationConfig(), world, blockStore, blockExecutor, report, scenario, extra);
    }

    private NodeStopper run(ConsensusReplay tool, ActivationConfig activationConfig, World world, BlockStore blockStore,
                            BlockExecutor blockExecutor, Path report, String scenario, String... extra) {
        List<String> argList = new ArrayList<>(List.of(
                "--fromBlock", "1",
                "--toBlock", "2",
                "--scenario", scenario,
                "--report", report.toString()));
        argList.addAll(List.of(extra));
        String[] args = argList.toArray(new String[0]);

        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(activationConfig).when(rskSystemProperties).getActivationConfig();
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();

        RskContext rskContext = mock(RskContext.class);
        doReturn(blockStore).when(rskContext).getBlockStore();
        doReturn(blockExecutor).when(rskContext).getBlockExecutor();
        doReturn(world.getStateRootHandler()).when(rskContext).getStateRootHandler();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();

        NodeStopper stopper = mock(NodeStopper.class);

        tool.execute(args, () -> rskContext, stopper);

        return stopper;
    }
}
