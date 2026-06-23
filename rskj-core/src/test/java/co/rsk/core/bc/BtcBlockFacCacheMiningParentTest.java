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
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.mine.BitcoinBlockRpcClient;
import co.rsk.mine.MinerUtils;
import co.rsk.mine.RegtestBtcMergeMiningHelper;
import co.rsk.test.mine.BitcoinRpcStubServer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static co.rsk.test.mine.BitcoinRpcStubServer.rpcError;
import static co.rsk.test.mine.BitcoinRpcStubServer.rpcOk;

class BtcBlockFacCacheMiningParentTest {

    private static final RegTestParams BTC_PARAMS = RegTestParams.get();
    private static final ActivationConfig ACTIVATION =
            ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

    @Test
    void resolveMiningParentForNewWork_usesRpcTipWhenAvailable() throws IOException {
        BtcBlock cachedParent = mineTwoTxBtcParent();
        String parentHex = Hex.toHexString(cachedParent.bitcoinSerialize());

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getbestblockhash", rpcOk("\"" + cachedParent.getHash() + "\""))
                .onMethod("getblockheader", rpcOk("{\"height\":1}"))
                .onMethod("getblock", rpcOk("\"" + parentHex + "\""))
                .start()) {
            BtcBlockFacCache cache = cacheWithRpc(server.url(), 0, 3, 50, 500);

            BtcMiningParentResolution resolution = cache.resolveMiningParentForNewWork().orElseThrow();

            Assertions.assertEquals(BtcMiningParentResolution.Source.RPC_TIP, resolution.getSource());
            Assertions.assertEquals(cachedParent.getHash(), resolution.getParent().getBlockHash());
        }
    }

    @Test
    void resolveMiningParentForNewWork_fallsBackToCacheWhenRpcFails() throws IOException {
        BtcBlock cachedParent = mineTwoTxBtcParent();

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getbestblockhash", rpcError(-1, "unavailable"))
                .start()) {
            BtcBlockFacCache cache = cacheWithRpc(server.url(), 0, 1, 0, 500);
            cache.recordFromFullBtcBlock(cachedParent, merkleProofFor(cachedParent));

            BtcMiningParentResolution resolution = cache.resolveMiningParentForNewWork().orElseThrow();

            Assertions.assertEquals(BtcMiningParentResolution.Source.CACHE_FALLBACK, resolution.getSource());
            Assertions.assertEquals(cachedParent.getHash(), resolution.getParent().getBlockHash());
        }
    }

    @Test
    void resolveMiningParentForNewWork_retriesRpcBeforeFallback() throws IOException {
        BtcBlock cachedParent = mineTwoTxBtcParent();
        String parentHex = Hex.toHexString(cachedParent.bitcoinSerialize());
        AtomicInteger bestHashCalls = new AtomicInteger();

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onDynamicMethod("getbestblockhash", () -> {
                    if (bestHashCalls.incrementAndGet() == 1) {
                        return rpcError(-1, "transient");
                    }
                    return rpcOk("\"" + cachedParent.getHash() + "\"");
                })
                .onMethod("getblockheader", rpcOk("{\"height\":1}"))
                .onMethod("getblock", rpcOk("\"" + parentHex + "\""))
                .start()) {
            BtcBlockFacCache cache = cacheWithRpc(server.url(), 0, 3, 50, 2_000);

            BtcMiningParentResolution resolution = cache.resolveMiningParentForNewWork().orElseThrow();

            Assertions.assertEquals(BtcMiningParentResolution.Source.RPC_TIP, resolution.getSource());
            Assertions.assertTrue(bestHashCalls.get() >= 2);
        }
    }

    @Test
    void resolveMiningParentForNewWork_emptyWhenNoRpcAndNoCache() {
        BtcBlockFacCache cache = new BtcBlockFacCache(
                new ForkBalanceBtcCacheConfig(12, "", 0, 5_000),
                BTC_PARAMS,
                null,
                ACTIVATION);

        Assertions.assertTrue(cache.resolveMiningParentForNewWork().isEmpty());
    }

    private static BtcBlockFacCache cacheWithRpc(
            String url,
            long requestDelayMs,
            int retryAttempts,
            long retryBackoffMs,
            long retryMaxBudgetMs) {
        ForkBalanceBtcCacheConfig config = new ForkBalanceBtcCacheConfig(
                12, url, requestDelayMs, 5_000, retryAttempts, retryBackoffMs, retryMaxBudgetMs, 1_500);
        return new BtcBlockFacCache(
                config,
                BTC_PARAMS,
                new BitcoinBlockRpcClient(config),
                ACTIVATION);
    }

    private static BtcBlock mineTwoTxBtcParent() {
        return RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(
                BTC_PARAMS,
                RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x55));
    }

    private static byte[] merkleProofFor(BtcBlock block) {
        return MinerUtils.buildMerkleProof(ACTIVATION, pb -> pb.buildFromBlock(block), 1L);
    }
}
