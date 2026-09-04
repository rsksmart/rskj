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

package co.rsk.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * External log-scraping tooling (RSKCORE-5789 sync verification) classifies consensus refusals and sync progress
 * from these exact log lines and logger names. Changing one is allowed but must be deliberate: update this test
 * and coordinate with the consumer. Same keep-in-sync idea as JsonRpcDocCoverageTest for doc/rpc.
 */
class ConsensusLogFormatStringsTest {

    private static final String BLOCK_EXECUTOR = "co/rsk/core/bc/BlockExecutor.java";
    private static final String BLOCK_CHAIN = "co/rsk/core/bc/BlockChainImpl.java";
    private static final String BLOCK_DIFFICULTY_RULE = "co/rsk/validators/BlockDifficultyRule.java";
    private static final String DIFFICULTY_RULE = "org/ethereum/validator/DifficultyRule.java";
    private static final String COMPOSITE_RULE = "co/rsk/validators/BlockCompositeRule.java";
    private static final String PARENT_COMPOSITE_RULE = "co/rsk/validators/BlockParentCompositeRule.java";
    private static final String SYSTEM_UTILS = "co/rsk/util/SystemUtils.java";
    private static final String NODE_RUNNER = "co/rsk/NodeRunnerImpl.java";
    private static final String CHANNEL_MANAGER = "org/ethereum/net/server/ChannelManagerImpl.java";
    private static final String ASYNC_BLOCK_PROCESSOR = "co/rsk/net/AsyncNodeBlockProcessor.java";
    private static final String BLOCK_SYNC_SERVICE = "co/rsk/net/BlockSyncService.java";

    static Stream<Arguments> pinnedLiterals() {
        return Stream.of(
            // execution refusal (classifier: FAIL, execution branch)
            Arguments.of(BLOCK_EXECUTOR, "getLogger(\"blockexecutor\")"),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] given State Root is invalid\""),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] given Receipt Root is invalid\""),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] given Logs Bloom is invalid\""),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] given gasUsed doesn't match: {} != {}\""),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] given paidFees doesn't match: {} != {}\""),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] given txs doesn't match: {} != {}\""),
            Arguments.of(BLOCK_EXECUTOR, "\"Block {} [{}] execution was interrupted because of an invalid transaction\""),
            // per-block outcome, local pre-execution refusal, node error
            Arguments.of(BLOCK_CHAIN, "getLogger(\"blockchain\")"),
            Arguments.of(BLOCK_CHAIN, "\"block: num: [{}] hash: [{}], processed after: [{}]seconds, result {}\""),
            Arguments.of(BLOCK_CHAIN, "\"Invalid block with number: {}\""),
            Arguments.of(BLOCK_CHAIN, "\"Unexpected error: \""),
            // pre-execution rule attribution (classifier: FAIL, validation branch)
            Arguments.of(BLOCK_DIFFICULTY_RULE, "getLogger(\"blockvalidator\")"),
            Arguments.of(BLOCK_DIFFICULTY_RULE, "\"#{}: difficulty != calcDifficulty\""),
            Arguments.of(DIFFICULTY_RULE, "\"#{}: difficulty != calcDifficulty\""),
            Arguments.of(COMPOSITE_RULE, "getLogger(\"blockvalidator\")"),
            Arguments.of(COMPOSITE_RULE, "\"Error Validating block {} {}\""),
            Arguments.of(PARENT_COMPOSITE_RULE, "getLogger(\"blockvalidator\")"),
            Arguments.of(PARENT_COMPOSITE_RULE, "\"Error Validating block {} {}\""),
            // post-import height beacon and network fingerprint (pre-flight gate).
            // SystemUtils holds the message; NodeRunnerImpl's "fullnoderunner" logger is the one that emits it,
            // so the scraper matches on that logger name.
            Arguments.of(SYSTEM_UTILS, "\"WARNING: Network upgrade {} is DISABLED. Best block number is: {}.\""),
            Arguments.of(SYSTEM_UTILS, "disabledNetworkUpgrade.name()"),
            Arguments.of(NODE_RUNNER, "getLogger(\"fullnoderunner\")"),
            // peer identity for peer.active construction
            Arguments.of(CHANNEL_MANAGER, "getLogger(\"net\")"),
            Arguments.of(CHANNEL_MANAGER, "\"Added new peer: {}. Total num of active peers: {}\"")
        );
    }

    /** The peer-relay refusal must stay colon-free: with a colon it would be misclassified as a local refusal. */
    static Stream<Arguments> forbiddenLiterals() {
        return Stream.of(
            Arguments.of(ASYNC_BLOCK_PROCESSOR, "\"Invalid block with number: "),
            Arguments.of(BLOCK_SYNC_SERVICE, "\"Invalid block with number: ")
        );
    }

    @ParameterizedTest(name = "{0} contains {1}")
    @MethodSource("pinnedLiterals")
    void sourceStillContainsPinnedLiteral(String relativeFile, String literal) throws IOException {
        assertTrue(readSource(relativeFile).contains(literal),
            "Pinned log literal not found in " + relativeFile + ": " + literal
                + " -- external log-scraping tooling depends on it; coordinate before changing");
    }

    @ParameterizedTest(name = "{0} must not contain {1}")
    @MethodSource("forbiddenLiterals")
    void sourceStillLacksForbiddenLiteral(String relativeFile, String literal) throws IOException {
        assertFalse(readSource(relativeFile).contains(literal),
            relativeFile + " gained the colon form of the peer-relay refusal; the no-colon variant is load-bearing");
    }

    private static Path mainJava() {
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path viaModule = dir.resolve("rskj-core/src/main/java");
            if (Files.isDirectory(viaModule)) {
                return viaModule;
            }
            Path direct = dir.resolve("src/main/java");
            if (Files.isDirectory(direct) && Files.isRegularFile(dir.resolve("src/main/resources/reference.conf"))) {
                return direct;
            }
        }
        throw new IllegalStateException("Could not find rskj-core/src/main/java above " + Paths.get("").toAbsolutePath());
    }

    private static String readSource(String relativeFile) throws IOException {
        Path file = mainJava().resolve(relativeFile);
        assertTrue(Files.exists(file), "source file moved: " + relativeFile);
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
