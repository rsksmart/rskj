/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.config.MainnetMergedConfigProperties;
import co.rsk.config.MergedNetworkConfig;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BtcBlockFacCache;
import co.rsk.core.bc.FacBlockHashesCache;
import co.rsk.test.mine.BitcoinRpcStubServer;
import co.rsk.test.mine.ForkBalanceMainnetMiningTestSupport;
import co.rsk.test.mine.ForkBalanceMainnetMiningTestSupport.ChainFixture;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static co.rsk.test.mine.BitcoinRpcStubServer.rpcOk;
import static co.rsk.test.mine.ForkBalanceMainnetMiningTestSupport.MAINNET_BTC;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * End-to-end mining under mainnet activation rules with a stubbed bitcoind JSON-RPC endpoint.
 */
class ForkBalanceMainnetMineSubmitE2ETest {

    @Test
    void mineAndSubmit_withBtcRpcStub_importsV3Block() throws IOException {
        BtcBlock btcMiningTip = ForkBalanceMainnetMiningTestSupport.mineTwoTxBtcParent((byte) 0x61);
        String btcTipHex = Hex.toHexString(btcMiningTip.bitcoinSerialize());

        try (BitcoinRpcStubServer rpc = BitcoinRpcStubServer.ok()
                .onMethod("getbestblockhash", rpcOk("\"" + btcMiningTip.getHash() + "\""))
                .onMethod("getblockheader", rpcOk("{\"height\":800000}"))
                .onMethod("getblock", rpcOk("\"" + btcTipHex + "\""))
                .start()) {
            MainnetMiningHarness harness = new MainnetMiningHarness(rpc.url());

            try {
                harness.minerServer.start();
                SubmitBlockResult result = harness.mineBestBlock();

                Assertions.assertEquals("OK", result.getStatus(), result.getMessage());
                Assertions.assertNotNull(result.getBlockInfo());
                Assertions.assertEquals(
                        "0x494d504f525445445f42455354",
                        result.getBlockInfo().getBlockImportedResult());

                Block mined = harness.chain.getBlockByHash(
                        HexUtils.stringHexToByteArray(result.getBlockInfo().getBlockHash()));
                Assertions.assertNotNull(mined);
                Assertions.assertEquals(MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT, mined.getNumber());
                Assertions.assertEquals((byte) 0x03, mined.getHeader().getVersion());

                byte[] forkBalanceProof = mined.getHeader().getForkBalanceProof();
                Assertions.assertNotNull(forkBalanceProof);
                Assertions.assertFalse(ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(forkBalanceProof));

                ForkBalanceProofUtils.ForkBalanceProofDecoded decoded =
                        ForkBalanceProofUtils.decodeForkBalanceProof(forkBalanceProof);
                Assertions.assertEquals(80, decoded.getParentBtcHeader().length);
                Assertions.assertTrue(decoded.getCoinbaseProof().length > 0);
                Assertions.assertEquals(btcMiningTip.getHash().toString(), harness.lastWork.getBtcParentBlockHash());
                Assertions.assertNotNull(harness.chain.getBlockFacFields(mined.getHash()));
                Assertions.assertTrue(rpc.requestCount("getbestblockhash") >= 1);
            } finally {
                harness.minerServer.stop();
            }
        }
    }

    @Test
    void mineTwoConsecutiveBlocks_rpcTipAdvancesAfterFirstSubmit() throws IOException {
        BtcBlock initialBtcTip = ForkBalanceMainnetMiningTestSupport.mineTwoTxBtcParent((byte) 0x62);
        AtomicReference<BtcBlock> currentTip = new AtomicReference<>(initialBtcTip);

        try (BitcoinRpcStubServer rpc = BitcoinRpcStubServer.ok()
                .onDynamicMethod("getbestblockhash", () ->
                        rpcOk("\"" + currentTip.get().getHash() + "\""))
                .onDynamicMethod("getblockheader", () -> rpcOk("{\"height\":800001}"))
                .onDynamicMethod("getblock", () ->
                        rpcOk("\"" + Hex.toHexString(currentTip.get().bitcoinSerialize()) + "\""))
                .start()) {
            MainnetMiningHarness harness = new MainnetMiningHarness(rpc.url());

            try {
                harness.minerServer.start();
                SubmitBlockResult first = harness.mineBestBlock();
                Assertions.assertEquals("OK", first.getStatus(), first.getMessage());

                currentTip.set(harness.lastMergedBtcBlock);

                harness.minerServer.buildBlockToMine(harness.chain.getBestBlock(), false);
                SubmitBlockResult second = harness.mineBestBlock();
                Assertions.assertEquals("OK", second.getStatus(), second.getMessage());
                Assertions.assertEquals(
                        MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT + 1,
                        harness.chain.getBestBlock().getNumber());

                byte[] proof = harness.chain.getBestBlock().getHeader().getForkBalanceProof();
                Assertions.assertFalse(ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(proof));
            } finally {
                harness.minerServer.stop();
            }
        }
    }

    private static final class MainnetMiningHarness {
        final BlockChainImpl chain;
        final MinerServerImpl minerServer;
        MinerWork lastWork;
        BtcBlock lastMergedBtcBlock;

        MainnetMiningHarness(String btcRpcUrl) {
            MainnetMergedConfigProperties props = spy(new MainnetMergedConfigProperties(
                    MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT));
            when(props.forkBalanceBtcCacheConfig()).thenReturn(
                    new ForkBalanceBtcCacheConfig(12, btcRpcUrl, 0, 5_000, 2, 50, 1_000, 500));

            FacBlockHashesCache facBlockHashesCache = new FacBlockHashesCache();
            BtcBlockFacCache btcBlockFacCache = new BtcBlockFacCache(
                    props.forkBalanceBtcCacheConfig(),
                    MAINNET_BTC,
                    new BitcoinBlockRpcClient(props.forkBalanceBtcCacheConfig()),
                    props.getActivationConfig());

            ChainFixture chainFixture = ForkBalanceMainnetMiningTestSupport.buildChainFixture(
                    props, facBlockHashesCache, btcBlockFacCache, true);
            chain = chainFixture.chain;
            minerServer = ForkBalanceMainnetMiningTestSupport.createMinerServer(
                    props, chainFixture.chainBuilder, chain, MAINNET_BTC,
                    facBlockHashesCache, btcBlockFacCache);
        }

        SubmitBlockResult mineBestBlock() {
            lastWork = minerServer.getWork();
            lastMergedBtcBlock = minerServer.buildBitcoinMergedMiningBlock(MAINNET_BTC, lastWork);
            ForkBalanceMainnetMiningTestSupport.bruteForceBtcNonce(lastWork, lastMergedBtcBlock);
            return minerServer.submitBitcoinBlock(
                    lastWork.getBlockHashForMergedMining(),
                    lastMergedBtcBlock);
        }
    }
}
