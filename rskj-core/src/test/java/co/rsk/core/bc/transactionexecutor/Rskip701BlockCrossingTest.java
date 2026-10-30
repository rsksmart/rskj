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

package co.rsk.core.bc.transactionexecutor;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static co.rsk.db.ClearedAccountAssertions.assertClearedAccount;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cardamom1000 (and so RSKIP701) activates at block {@link #ACTIVATION}.
 * Block 1 creates two CREATE2 children with nonce 1. C1 is destroyed in block N-1 and is deleted; C2 is destroyed
 * in block N and is cleared. Block N+1 re-creates both: only C1 can be re-created.
 * A node with the same config imports every block with the same state roots; a node without RSKIP701 imports the
 * blocks before N and rejects block N, whose state root it cannot reproduce. RSKIP545 requires RSKIP701, so that node
 * runs without RSKIP545 too; a node without RSKIP545 alone imports every block.
 */
class Rskip701BlockCrossingTest {

    private static final long ACTIVATION = 3;
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(2);
    private static final BigInteger CALL_GAS_LIMIT = BigInteger.valueOf(200_000);
    // PUSH1 42 PUSH1 0 SSTORE PUSH1 0 CALLDATALOAD SELFDESTRUCT
    private static final byte[] CODE_D = Hex.decode("602a600055600035ff");
    // Init code whose runtime is CODE_D
    private static final byte[] INIT_CODE_D = Hex.decode("68602a600055600035ff60005260096017f3");
    // MSTORE(0, INIT_CODE_D right-aligned) CREATE2(0, 14, 18, CALLDATALOAD(0)) POP STOP
    private static final byte[] CODE_FACTORY = Hex.decode(
            "7168602a600055600035ff60005260096017f3" + "600052" + "600035" + "6012" + "600e" + "6000" + "f5" + "5000");
    private static final RskAddress BENEFICIARY = new RskAddress("b1c7a1f0e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9");

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void destroyBeforeActivationDeletesAndAtActivationClears_andOnlyANodeWithRskip701ImportsTheActivationBlock(boolean parallel) {
        World miner = newWorld(true, true, parallel);
        Account sender = miner.getAccountByName("sender");
        RskAddress factory = miner.getAccountByName("factory").getAddress();
        RskAddress c1 = child(factory, 1);
        RskAddress c2 = child(factory, 2);

        List<Block> blocks = new ArrayList<>();
        blocks.add(buildAndConnect(miner, miner.getBlockChain().getBestBlock(),
                call(sender, 0, factory, salt(1)), call(sender, 1, factory, salt(2))));
        assertEquals(BigInteger.ONE, snapshot(miner).getNonce(c1));
        assertEquals(BigInteger.ONE, snapshot(miner).getNonce(c2));

        Transaction destroyC1 = call(sender, 2, c1, beneficiaryWord());
        blocks.add(buildAndConnect(miner, blocks.get(0), destroyC1));
        assertEquals(ACTIVATION - 1, blocks.get(1).getNumber());
        assertFalse(snapshot(miner).isExist(c1), "destroyed one block before activation: deleted");

        Transaction destroyC2 = call(sender, 3, c2, beneficiaryWord());
        blocks.add(buildAndConnect(miner, blocks.get(1), destroyC2));
        assertEquals(ACTIVATION, blocks.get(2).getNumber());
        assertEquals(parallel, blocks.get(2).getHeader().getTxExecutionSublistsEdges() != null,
                "the block must come from the expected execution path");
        assertClearedAccount(snapshot(miner), c2, BigInteger.ONE);
        assertEquals(gasUsed(miner, destroyC1), gasUsed(miner, destroyC2), "the refund is the same for a delete and a clear");

        Transaction recreateC1 = call(sender, 4, factory, salt(1));
        Transaction recreateC2 = call(sender, 5, factory, salt(2));
        blocks.add(buildAndConnect(miner, blocks.get(2), recreateC1, recreateC2));
        assertTrue(receipt(miner, recreateC1).isSuccessful());
        assertArrayEquals(CODE_D, snapshot(miner).getCode(c1));
        assertFalse(receipt(miner, recreateC2).isSuccessful(), "a cleared address keeps nonce 1, so CREATE2 collides");
        assertEquals(CALL_GAS_LIMIT.longValue(), gasUsed(miner, recreateC2));
        assertClearedAccount(snapshot(miner), c2, BigInteger.ONE);

        // A node with RSKIP701 imports every block and reaches the same state roots
        World follower = newWorld(true, true, parallel);
        for (Block block : blocks) {
            assertEquals(ImportResult.IMPORTED_BEST, follower.getBlockChain().tryToConnect(block));
            assertArrayEquals(block.getStateRoot(), follower.getBlockChain().getBestBlock().getStateRoot());
        }

        // RSKIP545 requires RSKIP701, so the node without RSKIP701 below runs without RSKIP545 too. A node without
        // RSKIP545 alone imports every block, so the rejection below comes from RSKIP701
        World withoutRskip545 = newWorld(true, false, parallel);
        for (Block block : blocks) {
            assertEquals(ImportResult.IMPORTED_BEST, withoutRskip545.getBlockChain().tryToConnect(block));
            assertArrayEquals(block.getStateRoot(), withoutRskip545.getBlockChain().getBestBlock().getStateRoot());
        }

        // A node without RSKIP701 follows until the first clear, at block N, and rejects it
        World oldNode = newWorld(false, false, parallel);
        assertEquals(ImportResult.IMPORTED_BEST, oldNode.getBlockChain().tryToConnect(blocks.get(0)));
        assertEquals(ImportResult.IMPORTED_BEST, oldNode.getBlockChain().tryToConnect(blocks.get(1)));
        assertEquals(ImportResult.INVALID_BLOCK, oldNode.getBlockChain().tryToConnect(blocks.get(2)));
        assertArrayEquals(blocks.get(1).getHash().getBytes(), oldNode.getBlockChain().getBestBlock().getHash().getBytes());
    }

    private static RskAddress child(RskAddress factory, int salt) {
        return new RskAddress(HashUtil.calcSaltAddr(factory, Bytes.of(INIT_CODE_D), salt(salt)));
    }

    private static byte[] salt(int salt) {
        return DataWord.valueOf(salt).getData();
    }

    private static byte[] beneficiaryWord() {
        return DataWord.valueOf(BENEFICIARY.getBytes()).getData();
    }

    private static Transaction call(Account sender, long nonce, RskAddress to, byte[] data) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(GAS_PRICE)
                .gasLimit(CALL_GAS_LIMIT)
                .receiveAddress(to)
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(Coin.ZERO)
                .data(data)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Block buildAndConnect(World world, Block parent, Transaction... txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(Arrays.asList(txs))
                .build(world.getConfig());
        assertEquals(txs.length, block.getTransactionsList().size());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
        return block;
    }

    private static TransactionReceipt receipt(World world, Transaction tx) {
        TransactionInfo info = world.getReceiptStore().getInMainChain(tx.getHash().getBytes(), world.getBlockStore()).orElseThrow();
        return info.getReceipt();
    }

    private static long gasUsed(World world, Transaction tx) {
        return new BigInteger(1, receipt(world, tx).getGasUsed()).longValue();
    }

    private static RepositorySnapshot snapshot(World world) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockChain().getBestBlock().getHeader());
    }

    private static World newWorld(boolean rskip701, boolean rskip545, boolean parallel) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> {
            Config withHeights = rawConfig
                    .withValue("blockchain.config.hardforkActivationHeights.cardamom1000", ConfigValueFactory.fromAnyRef(ACTIVATION))
                    .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(parallel ? 0 : -1))
                    .withValue("blockchain.config.consensusRules.rskip351", ConfigValueFactory.fromAnyRef(0));
            if (!rskip701) {
                withHeights = withHeights.withValue("blockchain.config.consensusRules.rskip701", ConfigValueFactory.fromAnyRef(-1));
            }
            if (!rskip545) {
                withHeights = withHeights.withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(-1));
            }
            return withHeights;
        });
        World world = new World(config);
        assertEquals(rskip701, world.getConfig().getActivationConfig().isActive(ConsensusRule.RSKIP701, ACTIVATION));
        assertFalse(world.getConfig().getActivationConfig().isActive(ConsensusRule.RSKIP701, ACTIVATION - 1));
        assertEquals(rskip545, world.getConfig().getActivationConfig().isActive(ConsensusRule.RSKIP545, ACTIVATION));
        world.saveAccount("sender", new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000L)).build());
        world.saveAccount("factory", new AccountBuilder(world).name("factory").balance(Coin.ZERO).code(CODE_FACTORY).build());
        return world;
    }
}
