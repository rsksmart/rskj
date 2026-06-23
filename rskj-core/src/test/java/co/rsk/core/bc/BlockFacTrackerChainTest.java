/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * FAC metadata is recorded on {@link BlockChainImpl#tryToConnect(Block)} and exposed via
 * {@link Blockchain#getBlockFacFields(Keccak256)}.
 */
class BlockFacTrackerChainTest {

    /**
     * All consensus rules active from genesis; {@link ConsensusRule#RSKIP144} off so block execution uses the
     * sequential path (matches common {@link BlockChainBuilder} tests and avoids header churn during parallel fill).
     */
    private static final class AllRulesActiveTestProperties extends TestSystemProperties {
        private final ActivationConfig activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

        @Override
        public ActivationConfig getActivationConfig() {
            return activationConfig;
        }
    }

    @Test
    void getBlockFacFields_withoutTracker_returnsNull() {
        BlockChainImpl chain = new BlockChainBuilder().setTesting(true).build();
        Block genesis = chain.getBestBlock();
        Assertions.assertNull(chain.getBlockFacFields(genesis.getHash()));
    }

    @Test
    void getBlockFacFields_shortChain_regtestHeaders_evidenceAndSafetyZero() {
        BlockFacTracker tracker = new BlockFacTracker();
        BlockChainBuilder builder = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(tracker);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator();
        Block block1 = gen.createChildBlock(genesis);
        builder.getBlockExecutor().executeAndFillAll(block1, genesis.getHeader());
        block1.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(block1));

        BlockFacFields gFac = chain.getBlockFacFields(genesis.getHash());
        Assertions.assertNotNull(gFac);
        Assertions.assertEquals(0, gFac.getFacEvidenceValue());
        Assertions.assertEquals(0, gFac.getFacSafetyLevel());
        Assertions.assertNull(gFac.getLastSafeBlock());

        BlockFacFields b1Fac = chain.getBlockFacFields(block1.getHash());
        Assertions.assertNotNull(b1Fac);
        Assertions.assertEquals(0, b1Fac.getFacEvidenceValue());
        Assertions.assertEquals(0, b1Fac.getFacSafetyLevel());
        Assertions.assertNull(b1Fac.getLastSafeBlock());
    }

    @Test
    void getBlockFacFields_unknownHash_returnsNull() {
        BlockFacTracker tracker = new BlockFacTracker();
        BlockChainImpl chain = new BlockChainBuilder()
                .setTesting(true)
                .setBlockFacTracker(tracker)
                .build();
        byte[] unknown = new byte[Keccak256.HASH_LEN];
        unknown[0] = 0x7f;
        Assertions.assertNull(chain.getBlockFacFields(new Keccak256(unknown)));
    }

    @Test
    void v3ProofTypeZero_setsEvidenceOne_andAdvancesLastSafe() {
        AllRulesActiveTestProperties props = new AllRulesActiveTestProperties();
        BlockFacTracker tracker = new BlockFacTracker();
        BlockChainBuilder builder = new BlockChainBuilder()
                .setConfig(props)
                .setTesting(true)
                .setBlockFacTracker(tracker);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), props.getActivationConfig());
        Block block1 = gen.createChildBlock(genesis);
        byte[] forkBalanceProof = ForkBalanceProofUtils.encodeForkBalanceProofSkeleton(
                (byte) 0, new byte[80], new byte[0], new byte[0], new byte[0], new byte[0]);
        block1.getHeader().setForkBalanceProof(forkBalanceProof);
        builder.getBlockExecutor().executeAndFillAll(block1, genesis.getHeader());
        block1.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(block1));

        BlockFacFields b1Fac = chain.getBlockFacFields(block1.getHash());
        Assertions.assertNotNull(b1Fac);
        Assertions.assertEquals(1, b1Fac.getFacEvidenceValue());
        Assertions.assertEquals(1, b1Fac.getFacSafetyLevel());
        Assertions.assertEquals(block1.getHash(), b1Fac.getLastSafeBlock());
    }
}
