/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.ethereum.rpc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockFacFields;
import co.rsk.core.bc.BlockFacTracker;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import co.rsk.util.HexUtils;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.core.ImportResult;
import org.ethereum.core.genesis.BlockTag;
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.parameters.BlockHashParam;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.BlockRefParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for FAC {@code forkSafe} JSON-RPC resolution.
 */
class ForkSafeRpcIntegrationTest {

    private static final class AllRulesActiveTestProperties extends TestSystemProperties {
        private final ActivationConfig activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

        @Override
        public ActivationConfig getActivationConfig() {
            return activationConfig;
        }
    }

    /**
     * Canonical tip is block 2; FAC {@code lastSafeBlock} on the tip points at block 1
     * (type-0 evidence on block 1, type-1 evidence on block 2 pulls safety back).
     */
    private static final class FacChainFixture {
        final BlockChainImpl chain;
        final Block genesis;
        final Block lastSafeBlock;
        final Block tip;
        final World world;

        FacChainFixture(BlockChainImpl chain, BlockChainBuilder builder, Block genesis, Block lastSafeBlock, Block tip) {
            this.chain = chain;
            this.genesis = genesis;
            this.lastSafeBlock = lastSafeBlock;
            this.tip = tip;
            this.world = ForkSafeRpcTestSupport.worldFromBuilder(chain, builder, (Genesis) genesis);
        }
    }

    @Test
    void eth_blockNumber_forkSafe_resolvesFacLastSafeFromConnectedChain() {
        FacChainFixture fixture = buildChainWithLastSafeBehindTip();
        Web3Impl web3 = ForkSafeRpcTestSupport.createWeb3(fixture.world);

        String blockNumber = web3.eth_blockNumber(true);

        Assertions.assertEquals(HexUtils.toQuantityJsonHex(fixture.lastSafeBlock.getNumber()), blockNumber);
        Assertions.assertNotEquals(HexUtils.toQuantityJsonHex(fixture.tip.getNumber()), blockNumber);
    }

    @Test
    void eth_getBlockByNumber_forkSafeTag_returnsFacLastSafeBlock() {
        FacChainFixture fixture = buildChainWithLastSafeBehindTip();
        Web3Impl web3 = ForkSafeRpcTestSupport.createWeb3(fixture.world);

        BlockResultDTO result = web3.eth_getBlockByNumber(
                new BlockIdentifierParam(BlockTag.FORK_SAFE.getTag()), false);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(fixture.lastSafeBlock.getHash().toJsonString(), result.getHash());
        Assertions.assertEquals(
                HexUtils.toQuantityJsonHex(fixture.lastSafeBlock.getNumber()),
                result.getNumber());
    }

    @Test
    void eth_getBlockByHash_forkSafeTrue_onTip_resolvesFacLastSafeBlock() {
        FacChainFixture fixture = buildChainWithLastSafeBehindTip();
        Web3Impl web3 = ForkSafeRpcTestSupport.createWeb3(fixture.world);

        BlockResultDTO result = web3.eth_getBlockByHash(
                new BlockHashParam(fixture.tip.getHash().toJsonString()), false, true);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(fixture.lastSafeBlock.getHash().toJsonString(), result.getHash());
    }

    @Test
    void eth_getBalance_forkSafeTag_readsStateAtFacLastSafeBlock() {
        FacChainFixture fixture = buildChainWithLastSafeBehindTip();
        Web3Impl web3 = ForkSafeRpcTestSupport.createWeb3(fixture.world);

        HexAddressParam address = new HexAddressParam(fixture.genesis.getCoinbase().toJsonString());

        String atExplicitLastSafe = web3.eth_getBalance(
                address, new BlockRefParam(HexUtils.toQuantityJsonHex(fixture.lastSafeBlock.getNumber())));
        String atForkSafeTag = web3.eth_getBalance(address, new BlockRefParam(BlockTag.FORK_SAFE.getTag()));

        Assertions.assertEquals(atExplicitLastSafe, atForkSafeTag);
    }

    @Test
    void eth_getBalance_requireForkSafeObject_readsStateAtFacLastSafeBlock() {
        FacChainFixture fixture = buildChainWithLastSafeBehindTip();
        Web3Impl web3 = ForkSafeRpcTestSupport.createWeb3(fixture.world);

        HexAddressParam address = new HexAddressParam(fixture.genesis.getCoinbase().toJsonString());

        String atExplicitLastSafe = web3.eth_getBalance(
                address, new BlockRefParam(HexUtils.toQuantityJsonHex(fixture.lastSafeBlock.getNumber())));
        String atRequireForkSafe = web3.eth_getBalance(address, new BlockRefParam(
                java.util.Map.of(
                        "blockNumber", HexUtils.toQuantityJsonHex(fixture.tip.getNumber()),
                        "requireForkSafe", "true")));

        Assertions.assertEquals(atExplicitLastSafe, atRequireForkSafe);
    }

    private static FacChainFixture buildChainWithLastSafeBehindTip() {
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
        block1.getHeader().setForkBalanceProof(skeletonProof((byte) 0));
        connect(builder, chain, block1, genesis);

        Block block2 = gen.createChildBlock(block1);
        block2.getHeader().setForkBalanceProof(skeletonProof((byte) 1));
        connect(builder, chain, block2, block1);

        BlockFacFields tipFac = chain.getBlockFacFields(block2.getHash());
        Assertions.assertNotNull(tipFac);
        Assertions.assertEquals(block1.getHash(), tipFac.getLastSafeBlock());
        Assertions.assertEquals(block2.getHash(), chain.getBestBlock().getHash());

        return new FacChainFixture(chain, builder, genesis, block1, block2);
    }

    private static void connect(BlockChainBuilder builder, BlockChainImpl chain, Block child, Block parent) {
        child.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        builder.getBlockExecutor().executeAndFillAll(child, parent.getHeader());
        child.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, chain.tryToConnect(child));
    }

    private static byte[] skeletonProof(byte proofType) {
        return ForkBalanceProofUtils.encodeForkBalanceProofSkeleton(
                proofType, new byte[80], new byte[0], new byte[0], new byte[0], new byte[0]);
    }
}
