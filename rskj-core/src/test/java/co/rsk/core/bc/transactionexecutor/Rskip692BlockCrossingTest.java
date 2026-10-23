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
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.BlockResult;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failing direct Bridge call (data 0xdeadbeef matches no method) in the block before RSKIP-692 and in the
 * activation block. Intrinsic cost = 21_000 + 4 * 16 = 21_064; the Bridge declares 23_000 for unknown data.
 * Before activation: status 1 and gasUsed 44_064 in the receipt, but the sender pays the full gas limit with no
 * refund. From activation: status 0, and gasUsed and the fee are both the gas limit.
 */
class Rskip692BlockCrossingTest {

    private static final long ACTIVATION = 2;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(100_000);
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(2);

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void failingBridgeCall_changesReceiptAndFeeAtActivation(boolean parallel) {
        MinedBlocks mined = mineAcrossActivation(parallel);

        TransactionReceipt preReceipt = receipt(mined.miner(), mined.preTx());
        assertTrue(preReceipt.isSuccessful(), "before RSKIP-692 the failing Bridge call keeps the legacy success status");
        assertEquals(BigInteger.valueOf(44_064), new BigInteger(1, preReceipt.getGasUsed()));
        assertEquals(new Coin(GAS_LIMIT.multiply(GAS_PRICE)), mined.initialBalance().subtract(mined.balanceAfterPre()),
                "before RSKIP-692 the sender pays the full gas limit, not the reported gasUsed, and gets no refund");

        TransactionReceipt atReceipt = receipt(mined.miner(), mined.atTx());
        assertFalse(atReceipt.isSuccessful(), "from RSKIP-692 the failing Bridge call is a failed tx");
        assertEquals(GAS_LIMIT, new BigInteger(1, atReceipt.getGasUsed()));
        assertEquals(new Coin(new BigInteger(1, atReceipt.getGasUsed()).multiply(GAS_PRICE)),
                mined.balanceAfterPre().subtract(mined.balanceAfterAt()),
                "from RSKIP-692 the sender pays gasUsed");
    }

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void aNodeWithoutRskip692_rejectsTheActivationBlock(boolean parallel) {
        MinedBlocks mined = mineAcrossActivation(parallel);
        Block preBlock = mined.preBlock();
        Block atBlock = mined.atBlock();

        // A node without RSKIP-692 accepts the block before activation and rejects the activation block,
        // whose receipts root it cannot reproduce
        World importer = newWorld(-1, parallel);
        assertEquals(ImportResult.IMPORTED_BEST, importer.getBlockChain().tryToConnect(preBlock));

        BlockResult importerResult = importer.getBlockExecutor().execute(
                null, 0, atBlock, preBlock.getHeader(), false, false, false);
        byte[] importerReceiptsRoot = BlockHashesHelper.calculateReceiptsTrieRoot(importerResult.getTransactionReceipts(), true);
        assertFalse(Arrays.equals(atBlock.getReceiptsRoot(), importerReceiptsRoot));
        assertTrue(importerResult.getTransactionReceipts().get(0).isSuccessful());

        assertEquals(ImportResult.INVALID_BLOCK, importer.getBlockChain().tryToConnect(atBlock));
        assertArrayEquals(preBlock.getHash().getBytes(), importer.getBlockChain().getBestBlock().getHash().getBytes());
    }

    private static MinedBlocks mineAcrossActivation(boolean parallel) {
        World miner = newWorld(ACTIVATION, parallel);
        Account sender = miner.getAccountByName("sender");
        Coin initialBalance = balance(miner, sender);

        Transaction preTx = bridgeCall(sender, 0);
        Block preBlock = buildAndConnect(miner, miner.getBlockChain().getBestBlock(), preTx);
        Coin balanceAfterPre = balance(miner, sender);
        Transaction atTx = bridgeCall(sender, 1);
        Block atBlock = buildAndConnect(miner, preBlock, atTx);
        Coin balanceAfterAt = balance(miner, sender);

        assertEquals(ACTIVATION - 1, preBlock.getNumber());
        assertEquals(ACTIVATION, atBlock.getNumber());
        assertEquals(parallel, atBlock.getHeader().getTxExecutionSublistsEdges() != null, "the block must come from the expected execution path");
        return new MinedBlocks(miner, preTx, preBlock, atTx, atBlock, initialBalance, balanceAfterPre, balanceAfterAt);
    }

    private static Transaction bridgeCall(Account sender, long nonce) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .receiveAddress(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(Coin.ZERO)
                .data(Hex.decode("deadbeef"))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Block buildAndConnect(World world, Block parent, Transaction tx) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(Collections.singletonList(tx))
                .build(world.getConfig());
        assertEquals(1, block.getTransactionsList().size());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
        return block;
    }

    private static TransactionReceipt receipt(World world, Transaction tx) {
        TransactionInfo info = world.getReceiptStore().getInMainChain(tx.getHash().getBytes(), world.getBlockStore()).orElseThrow();
        return info.getReceipt();
    }

    private static Coin balance(World world, Account account) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockChain().getBestBlock().getHeader()).getBalance(account.getAddress());
    }

    private static World newWorld(long rskip692Activation, boolean parallel) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip692", ConfigValueFactory.fromAnyRef(rskip692Activation))
                .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(parallel ? 0 : -1))
                .withValue("blockchain.config.consensusRules.rskip351", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sender", new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000L)).build());
        return world;
    }

    private record MinedBlocks(World miner, Transaction preTx, Block preBlock, Transaction atTx, Block atBlock,
                               Coin initialBalance, Coin balanceAfterPre, Coin balanceAfterAt) {
    }
}
