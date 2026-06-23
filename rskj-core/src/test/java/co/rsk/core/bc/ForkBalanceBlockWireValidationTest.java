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
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.TestSystemProperties;
import co.rsk.mine.ForkBalanceParentCoinbaseProof;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.mine.MinerServerImpl;
import co.rsk.mine.MinerUtils;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.ForkBalanceValidationRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.ImportResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wire-format, {@link BlockFactory#decodeBlock}, {@link BlockChainImpl#tryToConnect}, and
 * {@link ForkBalanceValidationRule} coverage for header v2/v3 and fork-balance proofs.
 */
class ForkBalanceBlockWireValidationTest {

    /**
     * Minimal coinbase whose wire serialization cannot contain the ASCII {@code RSKBLOCK:} merged-mining marker
     * (avoids flaky {@link ForkBalanceProofUtils#proofTypeIdentification} / tag scans on random secp256k1 pubkeys).
     */
    private static BtcTransaction btcCoinbaseWithoutRskTag(NetworkParameters params, byte marker) {
        BtcTransaction coinbaseTransaction = new BtcTransaction(params);
        coinbaseTransaction.addInput(new TransactionInput(params, coinbaseTransaction, new byte[]{marker}));
        coinbaseTransaction.addOutput(new TransactionOutput(
                params, coinbaseTransaction, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, marker}));
        return coinbaseTransaction;
    }

    /**
     * {@link ForkBalanceValidationRule} rejects an empty {@code coinbaseProof}; a 1-tx BTC parent yields an empty
     * RSKIP-92-style proof after {@link co.rsk.mine.Rskip92MerkleProofBuilder}, so tests need at least two txs.
     */
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

    private static final class AllButRskip144 extends TestSystemProperties {
        private final ActivationConfig activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

        @Override
        public ActivationConfig getActivationConfig() {
            return activationConfig;
        }
    }

    private static final class V2HeadersNo555 extends TestSystemProperties {
        private final ActivationConfig activationConfig =
                ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144, ConsensusRule.RSKIP555);

        @Override
        public ActivationConfig getActivationConfig() {
            return activationConfig;
        }
    }

    private static ForkBalanceValidationRule forkRule(TestSystemProperties props, FacBlockHashesCache cache) {
        return new ForkBalanceValidationRule(
                props.getActivationConfig(),
                props.getNetworkConstants().getBridgeConstants(),
                cache);
    }

    private static BlockChainBuilder forkBalanceChainBuilder(TestSystemProperties props) {
        return new BlockChainBuilder()
                .setConfig(props)
                .setTesting(false)
                .setForkBalanceValidationRule(forkRule(props, new FacBlockHashesCache()))
                .setFacBlockHashesCache(new FacBlockHashesCache());
    }

    @Test
    void v3Header_decodesRoundTrip_and_tryToConnect_withForkBalanceRule() {
        AllButRskip144 props = new AllButRskip144();
        BlockChainBuilder builder = forkBalanceChainBuilder(props);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), props.getActivationConfig());
        Block block1 = gen.createChildBlock(genesis);
        Assertions.assertEquals((byte) 0x03, block1.getHeader().getVersion());

        BlockFactory blockFactory = new BlockFactory(props.getActivationConfig());
        Block decoded = blockFactory.decodeBlock(block1.getEncoded());
        Assertions.assertEquals((byte) 0x03, decoded.getHeader().getVersion());
        Assertions.assertArrayEquals(
                block1.getHeader().getForkBalanceProof(),
                decoded.getHeader().getForkBalanceProof());

        builder.getBlockExecutor().executeAndFillAll(block1, genesis.getHeader());
        block1.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(block1));
    }

    @Test
    void v2Header_decodesRoundTrip_and_tryToConnect_withForkBalanceRule() {
        V2HeadersNo555 props = new V2HeadersNo555();
        BlockChainBuilder builder = forkBalanceChainBuilder(props);
        BlockChainImpl chain = builder.build();
        Block genesis = chain.getBestBlock();

        BlockGenerator gen = new BlockGenerator(Constants.regtest(), props.getActivationConfig());
        Block block1 = gen.createChildBlock(genesis);
        Assertions.assertEquals((byte) 0x02, block1.getHeader().getVersion());

        BlockFactory blockFactory = new BlockFactory(props.getActivationConfig());
        Block decoded = blockFactory.decodeBlock(block1.getEncoded());
        Assertions.assertEquals((byte) 0x02, decoded.getHeader().getVersion());

        builder.getBlockExecutor().executeAndFillAll(block1, genesis.getHeader());
        block1.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(block1));
    }

    @Test
    void v2Header_rejected_whenRskip555Active() {
        AllButRskip144 props555 = new AllButRskip144();
        V2HeadersNo555 propsV2 = new V2HeadersNo555();
        BlockGenerator gen = new BlockGenerator(Constants.regtest(), propsV2.getActivationConfig());
        Block genesis = new BlockChainBuilder().setConfig(propsV2).setTesting(true).build().getBestBlock();
        Block v2Block = gen.createChildBlock(genesis);
        Assertions.assertEquals((byte) 0x02, v2Block.getHeader().getVersion());

        ForkBalanceValidationRule rule = forkRule(props555, new FacBlockHashesCache());
        Assertions.assertFalse(rule.isValid(v2Block));
    }

    @Test
    void v2ShapedWire_rejectedAtDecode_whenRskip555Active() {
        AllButRskip144 propsV3 = new AllButRskip144();
        V2HeadersNo555 propsV2 = new V2HeadersNo555();
        BlockGenerator gen = new BlockGenerator(Constants.regtest(), propsV2.getActivationConfig());
        Block genesis = new BlockChainBuilder().setConfig(propsV2).setTesting(true).build().getBestBlock();
        Block v2Block = gen.createChildBlock(genesis);
        Assertions.assertEquals((byte) 0x02, v2Block.getHeader().getVersion());

        BlockFactory v3Factory = new BlockFactory(propsV3.getActivationConfig());
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> v3Factory.decodeBlock(v2Block.getEncoded()),
                "v2-shaped wire must fail at parse on a v3 chain, like v1 on v2");
    }

    @Test
    void buildForkBalanceProofSkeleton_matchesDecode_and_passesForkBalanceRule() {
        AllButRskip144 props = new AllButRskip144();
        ActivationConfig activation = props.getActivationConfig();
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
                1L);
        Assertions.assertTrue(
                coinbaseMerkleProof.length > 0,
                "fork-balance validation requires a non-empty coinbase merkle proof (see ForkBalanceValidationRule)");

        byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                Collections.emptyList(),
                mergedChild,
                btcParent,
                coinbaseMerkleProof);

        ForkBalanceProofUtils.ForkBalanceProofDecoded decoded = ForkBalanceProofUtils.decodeForkBalanceProof(proof);
        Assertions.assertEquals((byte) 2, decoded.getProofType());
        Assertions.assertArrayEquals(btcParent.cloneAsHeader().bitcoinSerialize(), decoded.getParentBtcHeader());
        Assertions.assertTrue(decoded.getCoinbaseProof().length > 0);
        Assertions.assertTrue(decoded.getCoinbaseLastBytes().length > 0);
        Assertions.assertEquals(32, decoded.getCoinbaseHash().length);
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.isValidMidstateWireBytes(decoded.getMidStateProof()));
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(
                decoded.getMidStateProof(),
                decoded.getCoinbaseLastBytes(),
                decoded.getCoinbaseHash()));

        BtcBlock mergedParsed = btcParams.getDefaultSerializer().makeBlock(mergedChild.cloneAsHeader().bitcoinSerialize());
        BtcBlock parentParsed = btcParams.getDefaultSerializer().makeBlock(decoded.getParentBtcHeader());
        Assertions.assertEquals(
                mergedParsed.getPrevBlockHash(),
                parentParsed.getHash(),
                "merged BTC header prev must match parent header hash");

        Assertions.assertArrayEquals(
                btcParent.getTransactions().get(0).getHash().getBytes(),
                decoded.getCoinbaseHash());

        Block block = new BlockGenerator(Constants.regtest(), activation).getBlock(1);
        Assertions.assertEquals((byte) 0x03, block.getHeader().getVersion());
        Block modified = new BlockFactory(activation).cloneBlockForModification(block);
        modified.getHeader().setForkBalanceProof(proof);
        modified.setBitcoinMergedMiningHeader(mergedChild.cloneAsHeader().bitcoinSerialize());
        modified.setBitcoinMergedMiningCoinbaseTransaction(
                MinerServerImpl.compressCoinbase(childCoinbase.bitcoinSerialize()));
        modified.setBitcoinMergedMiningMerkleProof(
                MinerUtils.buildMerkleProof(activation, pb -> pb.buildFromBlock(mergedChild), modified.getNumber()));

        ForkBalanceValidationRule rule = forkRule(props, new FacBlockHashesCache());
        Assertions.assertTrue(rule.isValid(modified));
    }

    @Test
    void v3PlaceholderForkBalance_withMergedMiningFieldsPresent_failsForkBalanceRule() {
        AllButRskip144 props = new AllButRskip144();
        ForkBalanceValidationRule rule = forkRule(props, new FacBlockHashesCache());
        Block block = new BlockGenerator(Constants.regtest(), props.getActivationConfig()).getBlock(1);
        Block b = new BlockFactory(props.getActivationConfig()).cloneBlockForModification(block);
        Assertions.assertEquals((byte) 0x03, b.getHeader().getVersion());
        b.getHeader().setForkBalanceProof(ForkBalanceProofUtils.defaultForkBalanceProofSkeletonBytes());
        b.setBitcoinMergedMiningHeader(new byte[80]);
        b.setBitcoinMergedMiningCoinbaseTransaction(new byte[128]);
        Assertions.assertFalse(rule.isValid(b));
    }

    @Test
    void v3NonPlaceholderForkBalance_wrongParentLink_failsForkBalanceRule() {
        AllButRskip144 props = new AllButRskip144();
        ActivationConfig activation = props.getActivationConfig();
        NetworkParameters btcParams = props.getNetworkConstants().getBridgeConstants().getBtcParams();

        BtcTransaction parentCoinbase = btcCoinbaseWithoutRskTag(btcParams, (byte) 0x31);
        BtcBlock btcParent = mineRegtestBtcParentWithTwoTxs(btcParams, activation, parentCoinbase);

        BtcTransaction childCoinbase = btcCoinbaseWithoutRskTag(btcParams, (byte) 0x32);
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
                1L);

        byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                Collections.emptyList(),
                mergedChild,
                btcParent,
                coinbaseMerkleProof);

        BtcBlock otherParent = mineRegtestBtcParentWithTwoTxs(
                btcParams, activation, btcCoinbaseWithoutRskTag(btcParams, (byte) 0x33));

        ForkBalanceProofUtils.ForkBalanceProofDecoded decodedProof = ForkBalanceProofUtils.decodeForkBalanceProof(proof);
        byte[] tamperedProof = ForkBalanceProofUtils.encodeForkBalanceProofSkeleton(
                (byte) 2,
                otherParent.cloneAsHeader().bitcoinSerialize(),
                decodedProof.getCoinbaseHash(),
                decodedProof.getCoinbaseProof(),
                decodedProof.getCoinbaseLastBytes(),
                decodedProof.getMidStateProof());

        Block block = new BlockGenerator(Constants.regtest(), activation).getBlock(1);
        Block b = new BlockFactory(activation).cloneBlockForModification(block);
        Assertions.assertEquals((byte) 0x03, b.getHeader().getVersion());
        b.getHeader().setForkBalanceProof(tamperedProof);
        b.setBitcoinMergedMiningHeader(mergedChild.cloneAsHeader().bitcoinSerialize());
        b.setBitcoinMergedMiningCoinbaseTransaction(
                MinerServerImpl.compressCoinbase(childCoinbase.bitcoinSerialize()));

        ForkBalanceValidationRule rule = forkRule(props, new FacBlockHashesCache());
        Assertions.assertFalse(rule.isValid(b));
    }

    @Test
    void v3NonPlaceholderForkBalance_badMerkleProof_failsForkBalanceRule() {
        AllButRskip144 props = new AllButRskip144();
        ActivationConfig activation = props.getActivationConfig();
        NetworkParameters btcParams = props.getNetworkConstants().getBridgeConstants().getBtcParams();

        BtcTransaction parentCoinbase = btcCoinbaseWithoutRskTag(btcParams, (byte) 0x41);
        BtcBlock btcParent = mineRegtestBtcParentWithTwoTxs(btcParams, activation, parentCoinbase);

        BtcTransaction childCoinbase = btcCoinbaseWithoutRskTag(btcParams, (byte) 0x42);
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

        byte[] goodProof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                Collections.emptyList(),
                mergedChild,
                btcParent,
                MinerUtils.buildMerkleProof(activation, pb -> pb.buildFromBlock(btcParent), 1L));

        ForkBalanceProofUtils.ForkBalanceProofDecoded goodDecoded = ForkBalanceProofUtils.decodeForkBalanceProof(goodProof);
        byte[] badProof = ForkBalanceProofUtils.encodeForkBalanceProofSkeleton(
                (byte) 2,
                goodDecoded.getParentBtcHeader(),
                goodDecoded.getCoinbaseHash(),
                new byte[]{0x01, 0x02, 0x03},
                goodDecoded.getCoinbaseLastBytes(),
                goodDecoded.getMidStateProof());

        Block block = new BlockGenerator(Constants.regtest(), activation).getBlock(1);
        Block b = new BlockFactory(activation).cloneBlockForModification(block);
        Assertions.assertEquals((byte) 0x03, b.getHeader().getVersion());
        b.getHeader().setForkBalanceProof(badProof);
        b.setBitcoinMergedMiningHeader(mergedChild.cloneAsHeader().bitcoinSerialize());
        b.setBitcoinMergedMiningCoinbaseTransaction(
                MinerServerImpl.compressCoinbase(childCoinbase.bitcoinSerialize()));

        ForkBalanceValidationRule rule = forkRule(props, new FacBlockHashesCache());
        Assertions.assertFalse(rule.isValid(b));
    }

    @Test
    void decodeForkBalanceProof_rejectsInvalidProofType() {
        byte[] encoded = ForkBalanceProofUtils.encodeForkBalanceProofSkeleton(
                (byte) 5, new byte[80], new byte[0], new byte[0], new byte[0], new byte[0]);
        Assertions.assertThrows(IllegalArgumentException.class, () -> ForkBalanceProofUtils.decodeForkBalanceProof(encoded));
    }
}
