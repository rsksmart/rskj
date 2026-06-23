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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.mine.MinerUtils;
import co.rsk.mine.RegtestBtcMergeMiningHelper;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BtcBlockFacCacheTest {

    private static final RegTestParams BTC_PARAMS = RegTestParams.get();
    private static final ActivationConfig ACTIVATION =
            ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

    @Test
    void resolveParent_findsRecordedMiningSubmit() {
        ForkBalanceBtcCacheConfig config = new ForkBalanceBtcCacheConfig(12, "", 0, 5_000);
        BtcBlockFacCache cache = new BtcBlockFacCache(config, BTC_PARAMS, null, ACTIVATION);

        BtcTransaction parentCoinbase = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x01);
        BtcBlock parent = RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(
                BTC_PARAMS, parentCoinbase);
        byte[] parentMerkle = MinerUtils.buildMerkleProof(
                ACTIVATION, pb -> pb.buildFromBlock(parent), 1L);
        cache.recordFromFullBtcBlock(parent, parentMerkle);

        BtcTransaction childCoinbase = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x02);
        BtcBlock child = RegtestBtcMergeMiningHelper.buildChildOnParent(BTC_PARAMS, parent, childCoinbase);
        byte[] childMerkle = MinerUtils.buildMerkleProof(
                ACTIVATION, pb -> pb.buildFromBlock(child), 1L);
        cache.recordFromMiningSubmit(child, childCoinbase, childMerkle, 1L);

        Assertions.assertTrue(cache.resolveParent(parent.getHash()).isPresent());
        CachedBtcBlockForFac resolved = cache.resolveParent(parent.getHash()).get();
        Assertions.assertEquals(parent.getHash(), resolved.getBlockHash());
        Assertions.assertTrue(resolved.isComplete());
    }

    @Test
    void evictsOldestHeightWhenNewHeightAppears() {
        ForkBalanceBtcCacheConfig config = new ForkBalanceBtcCacheConfig(2, "", 0, 5_000);
        BtcBlockFacCache cache = new BtcBlockFacCache(config, BTC_PARAMS, null, ACTIVATION);

        BtcTransaction cb0 = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x10);
        BtcBlock height0 = RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(BTC_PARAMS, cb0);
        cache.recordFromFullBtcBlock(height0, new byte[]{0x01});

        BtcTransaction cb1 = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x11);
        BtcBlock height1 = RegtestBtcMergeMiningHelper.buildChildOnParent(BTC_PARAMS, height0, cb1);
        cache.recordFromMiningSubmit(height1, cb1, new byte[]{0x02}, 1L);

        BtcTransaction cb2 = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x12);
        BtcBlock height2 = RegtestBtcMergeMiningHelper.buildChildOnParent(BTC_PARAMS, height1, cb2);
        cache.recordFromMiningSubmit(height2, cb2, new byte[]{0x03}, 2L);

        Assertions.assertTrue(cache.resolveParent(height1.getHash()).isPresent());
        Assertions.assertFalse(cache.resolveParent(height0.getHash()).isPresent());
    }

    @Test
    void resolveParent_withoutCacheOrRpc_returnsEmpty() {
        ForkBalanceBtcCacheConfig config = new ForkBalanceBtcCacheConfig(12, "", 0, 5_000);
        BtcBlockFacCache cache = new BtcBlockFacCache(config, BTC_PARAMS, null, ACTIVATION);
        Assertions.assertTrue(cache.resolveParent(Sha256Hash.wrap(new byte[32])).isEmpty());
    }

    @Test
    void resolveMiningParentForNewWork_withoutRpc_returnsHighestCompleteCachedBlock() {
        ForkBalanceBtcCacheConfig config = new ForkBalanceBtcCacheConfig(12, "", 0, 5_000);
        BtcBlockFacCache cache = new BtcBlockFacCache(config, BTC_PARAMS, null, ACTIVATION);

        BtcTransaction cb0 = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x20);
        BtcBlock height0 = RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(BTC_PARAMS, cb0);
        cache.recordFromFullBtcBlock(height0, new byte[]{0x01});

        BtcTransaction cb1 = RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x21);
        BtcBlock height1 = RegtestBtcMergeMiningHelper.buildChildOnParent(BTC_PARAMS, height0, cb1);
        cache.recordFromMiningSubmit(height1, cb1, new byte[]{0x02}, 1L);

        CachedBtcBlockForFac tip = cache.resolveMiningParentForNewWork().orElseThrow().getParent();
        Assertions.assertEquals(height1.getHash(), tip.getBlockHash());
    }
}
