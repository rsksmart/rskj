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
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ConfigUtils;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.mine.BlockToMineBuilder;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.mine.ForkDetectionDataCalculator;
import co.rsk.mine.GasLimitCalculator;
import co.rsk.mine.MinerClock;
import co.rsk.mine.MinerServerImpl;
import co.rsk.mine.MinerUtils;
import co.rsk.mine.MinerWork;
import co.rsk.mine.MinimumGasPriceCalculator;
import co.rsk.mine.SubmitBlockResult;
import co.rsk.mine.gas.provider.FixedMinGasPriceProvider;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.ForkBalanceValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import co.rsk.util.HexUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.util.BuildInfo;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;

/**
 * End-to-end path: {@link MinerServerImpl} builds a chained regtest BTC block, resolves the parent from
 * {@link BtcBlockFacCache}, attaches a fork-balance proof, and imports a v3 RSK block through the real chain.
 */
class ForkBalanceMinerIntegrationTest {

    private static final class AllButRskip144 extends TestSystemProperties {
        private final ActivationConfig activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

        @Override
        public ActivationConfig getActivationConfig() {
            return activationConfig;
        }
    }

    @Test
    void mineV3Block_throughMinerServer_resolvesBtcParentFromCache_andImports() {
        MiningFixture fixture = new MiningFixture();

        try {
            fixture.minerServer.start();
            SubmitBlockResult result = fixture.mineBestBlock();

            Assertions.assertEquals("OK", result.getStatus(), result.getMessage());
            Assertions.assertNotNull(result.getBlockInfo());
            Assertions.assertEquals(
                    "0x494d504f525445445f42455354",
                    result.getBlockInfo().getBlockImportedResult());

            Block mined = fixture.chain.getBlockByHash(
                    HexUtils.stringHexToByteArray(result.getBlockInfo().getBlockHash()));
            Assertions.assertNotNull(mined);
            Assertions.assertEquals(1L, mined.getNumber());
            Assertions.assertEquals((byte) 0x03, mined.getHeader().getVersion());

            byte[] forkBalanceProof = mined.getHeader().getForkBalanceProof();
            Assertions.assertNotNull(forkBalanceProof);
            Assertions.assertFalse(ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(forkBalanceProof));

            ForkBalanceProofUtils.ForkBalanceProofDecoded decoded =
                    ForkBalanceProofUtils.decodeForkBalanceProof(forkBalanceProof);
            Assertions.assertTrue(decoded.getCoinbaseProof().length > 0);
            Assertions.assertTrue(decoded.getParentBtcHeader().length == 80);

            Assertions.assertNotNull(fixture.chain.getBlockFacFields(mined.getHash()));
        } finally {
            fixture.minerServer.stop();
        }
    }

    @Test
    void mineTwoConsecutiveV3Blocks_secondUsesParentCachedFromFirstSubmit() {
        MiningFixture fixture = new MiningFixture();

        try {
            fixture.minerServer.start();
            SubmitBlockResult first = fixture.mineBestBlock();
            Assertions.assertEquals("OK", first.getStatus(), first.getMessage());

            fixture.minerServer.buildBlockToMine(fixture.chain.getBestBlock(), false);
            SubmitBlockResult second = fixture.mineBestBlock();
            Assertions.assertEquals("OK", second.getStatus(), second.getMessage());
            Assertions.assertEquals(
                    "0x494d504f525445445f42455354",
                    second.getBlockInfo().getBlockImportedResult(),
                    "second block import result");
            Assertions.assertEquals(2L, fixture.chain.getBestBlock().getNumber());

            byte[] proof = fixture.chain.getBestBlock().getHeader().getForkBalanceProof();
            Assertions.assertFalse(ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(proof));
        } finally {
            fixture.minerServer.stop();
        }
    }

    private static final class MiningFixture {
        final AllButRskip144 props = new AllButRskip144();
        final FacBlockHashesCache facBlockHashesCache = new FacBlockHashesCache();
        final BtcBlockFacCache btcBlockFacCache;
        final BlockFacTracker blockFacTracker = new BlockFacTracker();
        final BlockChainBuilder chainBuilder;
        final BlockChainImpl chain;
        final MinerServerImpl minerServer;

        MiningFixture() {
            btcBlockFacCache = new BtcBlockFacCache(
                    new ForkBalanceBtcCacheConfig(12, "", 0, 5_000),
                    RegTestParams.get(),
                    null,
                    props.getActivationConfig());
            ForkBalanceValidationRule forkBalanceValidationRule = new ForkBalanceValidationRule(
                    props.getActivationConfig(),
                    props.getNetworkConstants().getBridgeConstants(),
                    facBlockHashesCache);
            chainBuilder = new BlockChainBuilder()
                    .setConfig(props)
                    .setTesting(false)
                    .setBlockFacTracker(blockFacTracker)
                    .setForkBalanceValidationRule(forkBalanceValidationRule)
                    .setFacBlockHashesCache(facBlockHashesCache)
                    .setBtcBlockFacCache(btcBlockFacCache);
            chain = chainBuilder.build();

            SimpleEthereum ethereum = new SimpleEthereum(chain);
            MiningMainchainView mainchainView = new MiningMainchainViewImpl(
                    chainBuilder.getBlockStore(),
                    MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION);
            MiningConfig miningConfig = ConfigUtils.getDefaultMiningConfig();
            MinerClock clock = new MinerClock(true, Clock.systemUTC());
            SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
            BlockFactory blockFactory = new BlockFactory(props.getActivationConfig());

            minerServer = new MinerServerImpl(
                    props,
                    ethereum,
                    mainchainView,
                    null,
                    new ProofOfWorkRule(props).setFallbackMiningEnabled(false),
                    new BlockToMineBuilder(
                            props.getActivationConfig(),
                            miningConfig,
                            chainBuilder.getRepositoryLocator(),
                            chainBuilder.getBlockStore(),
                            chainBuilder.getTransactionPool(),
                            new DifficultyCalculator(props.getActivationConfig(), props.getNetworkConstants()),
                            new GasLimitCalculator(props.getNetworkConstants()),
                            new ForkDetectionDataCalculator(RegTestParams.get()),
                            block -> true,
                            clock,
                            blockFactory,
                            chainBuilder.getBlockExecutor(),
                            new MinimumGasPriceCalculator(new FixedMinGasPriceProvider(props.minerMinGasPrice())),
                            new MinerUtils(),
                            signatureCache),
                    clock,
                    blockFactory,
                    new BuildInfo("test", "test"),
                    miningConfig,
                    facBlockHashesCache,
                    btcBlockFacCache,
                    RegTestParams.get());
        }

        SubmitBlockResult mineBestBlock() {
            MinerWork work = minerServer.getWork();
            BtcBlock bitcoinMergedMiningBlock = minerServer.buildBitcoinMergedMiningBlock(RegTestParams.get(), work);
            findNonce(work, bitcoinMergedMiningBlock);
            return minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);
        }

        private static void findNonce(MinerWork work, BtcBlock bitcoinMergedMiningBlock) {
            BigInteger target = new BigInteger(HexUtils.stringHexToByteArray(work.getTarget()));
            while (true) {
                try {
                    if (bitcoinMergedMiningBlock.getHash().toBigInteger().compareTo(target) <= 0) {
                        return;
                    }
                    bitcoinMergedMiningBlock.setNonce(bitcoinMergedMiningBlock.getNonce() + 1);
                } catch (VerificationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
