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
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.MainnetMergedConfigProperties;
import co.rsk.config.MergedNetworkConfig;
import co.rsk.mine.ForkBalanceParentCoinbaseProof;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.mine.MinerServerImpl;
import co.rsk.mine.MinerUtils;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.ForkBalanceValidationRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.ImportResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RSKIP555 activation boundary checks using merged mainnet config.
 * Height is shifted to {@link MergedNetworkConfig#SHORT_CHAIN_VETIVER_HEIGHT} for short chains.
 */
class MainnetVetiverActivationTest {

    @Test
    void blockFactory_producesV0BeforeVetiver_andV3AtVetiver() {
        MainnetMergedConfigProperties props = new MainnetMergedConfigProperties(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT);
        ActivationConfig activation = props.getActivationConfig();
        BlockChainBuilder builder = new BlockChainBuilder().setConfig(props).setTesting(true);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), activation);
        Block preVetiver = gen.createChildBlock(genesis);
        Assertions.assertEquals(1L, preVetiver.getNumber());
        Assertions.assertEquals((byte) 0x00, preVetiver.getHeader().getVersion());

        builder.getBlockExecutor().executeAndFillAll(preVetiver, genesis.getHeader());
        preVetiver.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(preVetiver));

        Block atVetiver = gen.createChildBlock(preVetiver);
        Assertions.assertEquals(MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT, atVetiver.getNumber());
        Assertions.assertEquals((byte) 0x03, atVetiver.getHeader().getVersion());
    }

    @Test
    void v3BlockAtVetiver_withForkBalanceProof_importsThroughForkBalanceRule() {
        MainnetMergedConfigProperties props = new MainnetMergedConfigProperties(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT);
        ActivationConfig activation = props.getActivationConfig();
        FacBlockHashesCache facCache = new FacBlockHashesCache();
        ForkBalanceValidationRule forkRule = new ForkBalanceValidationRule(
                activation,
                props.getNetworkConstants().getBridgeConstants(),
                facCache);

        BlockChainBuilder builder = new BlockChainBuilder()
                .setConfig(props)
                .setTesting(false)
                .setForkBalanceValidationRule(forkRule)
                .setFacBlockHashesCache(facCache);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), activation);
        Block preVetiver = gen.createChildBlock(genesis);
        builder.getBlockExecutor().executeAndFillAll(preVetiver, genesis.getHeader());
        preVetiver.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(preVetiver));

        Block vetiverBlock = buildV3BlockWithForkBalanceProof(props, activation, preVetiver);
        Assertions.assertEquals(MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT, vetiverBlock.getNumber());
        Assertions.assertEquals((byte) 0x03, vetiverBlock.getHeader().getVersion());

        builder.getBlockExecutor().executeAndFillAll(vetiverBlock, preVetiver.getHeader());
        vetiverBlock.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(vetiverBlock));
    }

    @Test
    void preVetiverBlock_importsWithoutForkBalanceRuleRejection() {
        MainnetMergedConfigProperties props = new MainnetMergedConfigProperties(
                MergedNetworkConfig.SHORT_CHAIN_VETIVER_HEIGHT);
        ActivationConfig activation = props.getActivationConfig();
        ForkBalanceValidationRule forkRule = new ForkBalanceValidationRule(
                activation,
                props.getNetworkConstants().getBridgeConstants(),
                new FacBlockHashesCache());

        BlockChainBuilder builder = new BlockChainBuilder()
                .setConfig(props)
                .setTesting(false)
                .setForkBalanceValidationRule(forkRule);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), activation);
        Block preVetiver = gen.createChildBlock(genesis);
        builder.getBlockExecutor().executeAndFillAll(preVetiver, genesis.getHeader());
        preVetiver.seal();

        Assertions.assertTrue(forkRule.isValid(preVetiver));
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(preVetiver));
    }

    private static Block buildV3BlockWithForkBalanceProof(
            MainnetMergedConfigProperties props,
            ActivationConfig activation,
            Block parent) {
        NetworkParameters btcParams = props.getNetworkConstants().getBridgeConstants().getBtcParams();
        BtcTransaction parentCoinbase = btcCoinbaseWithoutRskTag(btcParams, (byte) 0x11);
        BtcBlock btcParent = mineRegtestBtcParentWithTwoTxs(btcParams, activation, parentCoinbase);

        BtcTransaction childCoinbase = btcCoinbaseWithoutRskTag(btcParams, (byte) 0x22);
        BtcBlock mergedChild = new BtcBlock(
                btcParams,
                btcParams.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                btcParent.getHash(),
                null,
                btcParent.getTimeSeconds() + 600,
                btcParent.getDifficultyTarget(),
                0,
                Collections.singletonList(childCoinbase));
        new BlockMiner(activation).findNonce(mergedChild, btcParams.getMaxTarget());

        byte[] coinbaseMerkleProof = MinerUtils.buildMerkleProof(
                activation,
                pb -> pb.buildFromBlock(btcParent),
                parent.getNumber() + 1);
        byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                Collections.emptyList(),
                mergedChild,
                btcParent,
                coinbaseMerkleProof);

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), activation);
        Block block = gen.createChildBlock(parent);
        Block modified = new BlockFactory(activation).cloneBlockForModification(block);
        modified.getHeader().setForkBalanceProof(proof);
        modified.setBitcoinMergedMiningHeader(mergedChild.cloneAsHeader().bitcoinSerialize());
        modified.setBitcoinMergedMiningCoinbaseTransaction(
                MinerServerImpl.compressCoinbase(childCoinbase.bitcoinSerialize()));
        modified.setBitcoinMergedMiningMerkleProof(
                MinerUtils.buildMerkleProof(activation, pb -> pb.buildFromBlock(mergedChild), modified.getNumber()));

        ForkBalanceProofUtils.ForkBalanceProofDecoded decoded = ForkBalanceProofUtils.decodeForkBalanceProof(proof);
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.isValidMidstateWireBytes(decoded.getMidStateProof()));

        return modified;
    }

    private static BtcTransaction btcCoinbaseWithoutRskTag(NetworkParameters params, byte marker) {
        BtcTransaction coinbaseTransaction = new BtcTransaction(params);
        coinbaseTransaction.addInput(new TransactionInput(params, coinbaseTransaction, new byte[]{marker}));
        coinbaseTransaction.addOutput(new TransactionOutput(
                params, coinbaseTransaction, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, marker}));
        return coinbaseTransaction;
    }

    private static BtcBlock mineRegtestBtcParentWithTwoTxs(
            NetworkParameters btcParams,
            ActivationConfig activation,
            BtcTransaction parentCoinbase) {
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addInput(new TransactionInput(
                btcParams,
                tx2,
                new byte[0],
                new TransactionOutPoint(btcParams, 0, parentCoinbase.getHash())));
        tx2.addOutput(new TransactionOutput(
                btcParams,
                tx2,
                Coin.valueOf(0, 0),
                new byte[]{ScriptOpCodes.OP_RETURN, (byte) 0x7d}));
        List<BtcTransaction> txs = new ArrayList<>();
        txs.add(parentCoinbase);
        txs.add(tx2);
        BtcBlock block = new BtcBlock(
                btcParams,
                btcParams.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                Sha256Hash.ZERO_HASH,
                null,
                (System.currentTimeMillis() / 1000) - 1_000_000,
                Utils.encodeCompactBits(btcParams.getMaxTarget()),
                0,
                txs);
        new BlockMiner(activation).findNonce(block, btcParams.getMaxTarget());
        return block;
    }
}
