/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.test.mine;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ConfigUtils;
import co.rsk.config.MainnetMergedConfigProperties;
import co.rsk.config.MergedNetworkConfig;
import co.rsk.config.MiningConfig;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockFacTracker;
import co.rsk.core.bc.BtcBlockFacCache;
import co.rsk.core.bc.FacBlockHashesCache;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.core.bc.MiningMainchainViewImpl;
import co.rsk.mine.BlockToMineBuilder;
import co.rsk.mine.ForkDetectionDataCalculator;
import co.rsk.mine.GasLimitCalculator;
import co.rsk.mine.MinerClock;
import co.rsk.mine.MinerServerImpl;
import co.rsk.mine.MinerUtils;
import co.rsk.mine.MinerWork;
import co.rsk.mine.MinimumGasPriceCalculator;
import co.rsk.mine.RegtestBtcMergeMiningHelper;
import co.rsk.mine.gas.provider.FixedMinGasPriceProvider;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.HexUtils;
import co.rsk.validators.ForkBalanceValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ImportResult;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.util.BuildInfo;
import org.junit.jupiter.api.Assertions;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.time.Clock;

/**
 * Shared wiring for mainnet-activation fork-balance mining tests.
 */
public final class ForkBalanceMainnetMiningTestSupport {

    public static final NetworkParameters MAINNET_BTC =
            Constants.mainnet().getBridgeConstants().getBtcParams();

    private ForkBalanceMainnetMiningTestSupport() {
    }

    public static BtcBlock mineTwoTxBtcParent(byte coinbaseNonce) {
        RegTestParams regtest = RegTestParams.get();
        return RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(
                regtest,
                RegtestBtcMergeMiningHelper.neutralCoinbase(regtest, coinbaseNonce));
    }

    public static void seedBtcMiningTip(BtcBlockFacCache cache, ActivationConfig activationConfig) {
        BtcBlock parent = mineTwoTxBtcParent((byte) 0x31);
        byte[] parentMerkle = MinerUtils.buildMerkleProof(
                activationConfig, pb -> pb.buildFromBlock(parent), 1L);
        cache.recordFromFullBtcBlock(parent, parentMerkle);
    }

    public static void connectPreVetiverParent(
            BlockChainBuilder chainBuilder,
            BlockChainImpl chain,
            MainnetMergedConfigProperties props) {
        Block genesis = chain.getBestBlock();
        BlockGenerator gen = new BlockGenerator(Constants.regtest(), props.getActivationConfig());
        Block preVetiver = gen.createChildBlock(genesis);
        chainBuilder.getBlockExecutor().executeAndFillAll(preVetiver, genesis.getHeader());
        preVetiver.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(preVetiver));
        Assertions.assertEquals(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT - 1,
                chain.getBestBlock().getNumber());
    }

    public static ChainFixture buildChainFixture(
            MainnetMergedConfigProperties props,
            FacBlockHashesCache facBlockHashesCache,
            BtcBlockFacCache btcBlockFacCache,
            boolean withForkBalanceValidation) {
        BlockChainBuilder chainBuilder = new BlockChainBuilder()
                .setConfig(props)
                .setTesting(false)
                .setFacBlockHashesCache(facBlockHashesCache)
                .setBtcBlockFacCache(btcBlockFacCache);
        if (withForkBalanceValidation) {
            chainBuilder
                    .setBlockFacTracker(new BlockFacTracker())
                    .setForkBalanceValidationRule(new ForkBalanceValidationRule(
                            props.getActivationConfig(),
                            props.getNetworkConstants().getBridgeConstants(),
                            facBlockHashesCache));
        }
        BlockChainImpl chain = chainBuilder.build();
        connectPreVetiverParent(chainBuilder, chain, props);
        return new ChainFixture(chainBuilder, chain);
    }

    public static final class ChainFixture {
        public final BlockChainBuilder chainBuilder;
        public final BlockChainImpl chain;

        public ChainFixture(BlockChainBuilder chainBuilder, BlockChainImpl chain) {
            this.chainBuilder = chainBuilder;
            this.chain = chain;
        }
    }

    public static MinerServerImpl createMinerServer(
            MainnetMergedConfigProperties props,
            BlockChainBuilder chainBuilder,
            BlockChainImpl chain,
            NetworkParameters mainnetBtc,
            FacBlockHashesCache facBlockHashesCache,
            @Nullable BtcBlockFacCache btcBlockFacCache) {
        MiningMainchainView mainchainView = new MiningMainchainViewImpl(
                chainBuilder.getBlockStore(),
                MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION);
        SimpleEthereum ethereum = new SimpleEthereum(chain);
        MiningConfig miningConfig = ConfigUtils.getDefaultMiningConfig();
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BlockFactory blockFactory = new BlockFactory(props.getActivationConfig());

        return new MinerServerImpl(
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
                        new ForkDetectionDataCalculator(mainnetBtc),
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
                mainnetBtc);
    }

    public static void bruteForceBtcNonce(MinerWork work, BtcBlock bitcoinMergedMiningBlock) {
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
