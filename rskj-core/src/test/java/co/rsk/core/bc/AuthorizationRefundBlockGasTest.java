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
package co.rsk.core.bc;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block where the second transaction fits only because the first one, a type-4 transaction, got the
 * authorization refund. The block gas check adds a transaction's gas limit to the gas the earlier transactions
 * used after refunds, so the refund frees room in the block. Checked on the miner side (the transaction is
 * kept or dropped) and on the validation side (the block is accepted or rejected).
 *
 * <p>Most cases run with RSKIP-144 off, so every transaction runs in one sequential list whose limit is the block
 * gas limit. The last two cases run with RSKIP-144 on, where the limit that matters is the one of the sublist both
 * transactions share because they have the same sender.
 */
class AuthorizationRefundBlockGasTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final long BLOCK_GAS_LIMIT = 1_000_000L;
    // Large enough to give the parallel sublists gas when RSKIP-144 is on
    private static final long PARALLEL_BLOCK_GAS_LIMIT = 20_000_000L;
    private static final long AUTHORIZATION_REFUND = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;
    // A type-4 call with no data and one authorization tuple
    private static final long TYPE4_INTRINSIC_GAS = GasCost.TRANSACTION + GasCost.PER_EMPTY_ACCOUNT_COST;
    private static final long TYPE4_GAS_USED_WITH_REFUND = TYPE4_INTRINSIC_GAS - AUTHORIZATION_REFUND;
    private static final long GAS_LEFT_AFTER_REFUND = BLOCK_GAS_LIMIT - TYPE4_GAS_USED_WITH_REFUND;

    @Test
    void type4TxToExistingAuthority_usesIntrinsicGasMinusTheRefund() {
        World world = newWorld(-1);
        Account authority = fundedAccount(world, "authority");

        Block block = mine(world, List.of(delegation(world, authority)));

        assertEquals(1, block.getTransactionsList().size());
        assertEquals(TYPE4_GAS_USED_WITH_REFUND, gasUsed(world, block, 0));
        assertEquals(9_500L, AUTHORIZATION_REFUND);
    }

    @Test
    void minerKeepsTxThatFitsOnlyBecauseOfTheRefund_andTheBlockIsValid() {
        World world = newWorld(-1);
        Account authority = fundedAccount(world, "authority");

        Transaction tx2 = transfer(world, "other", 0, GAS_LEFT_AFTER_REFUND);
        Block block = mine(world, List.of(delegation(world, authority), tx2));

        assertEquals(2, block.getTransactionsList().size(), "the miner must keep the tx that uses exactly the gas left");
        assertEquals(TYPE4_GAS_USED_WITH_REFUND, gasUsed(world, block, 0));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
        assertEquals(TYPE4_GAS_USED_WITH_REFUND + GasCost.TRANSACTION, block.getGasUsed());
    }

    @Test
    void minerDropsTxOneGasLargerThanTheGasLeftAfterTheRefund() {
        World world = newWorld(-1);
        Account authority = fundedAccount(world, "authority");

        Transaction tx2 = transfer(world, "other", 0, GAS_LEFT_AFTER_REFUND + 1);
        Block block = mine(world, List.of(delegation(world, authority), tx2));

        assertEquals(1, block.getTransactionsList().size(), "the miner must drop the tx that is one gas too large");
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
    }

    @Test
    void withoutTheRefund_minerDropsTheSameTx() {
        // The authority is an empty account, so the tuple gets no refund and the first tx uses its full
        // intrinsic gas. The tx that fitted with the refund no longer fits: the refund is what made room.
        World world = newWorld(-1);
        Account emptyAuthority = new AccountBuilder().name("emptyAuthority").build();

        Transaction tx2 = transfer(world, "other", 0, GAS_LEFT_AFTER_REFUND);
        Block block = mine(world, List.of(delegation(world, emptyAuthority), tx2));

        assertEquals(1, block.getTransactionsList().size());
        assertEquals(TYPE4_INTRINSIC_GAS, gasUsed(world, block, 0));
    }

    @Test
    void validation_acceptsBlockWhoseLastTxFitsOnlyBecauseOfTheRefund() {
        World world = newWorld(-1);
        Account authority = fundedAccount(world, "authority");
        Block parent = world.getBlockChain().getBestBlock();

        Block block = blockWithoutMinerSelection(world, parent, delegation(world, authority), transfer(world, "other", 0, GAS_LEFT_AFTER_REFUND));

        BlockResult result = validate(world, block, parent);
        assertNotSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, result);
        assertEquals(TYPE4_GAS_USED_WITH_REFUND + GasCost.TRANSACTION, result.getGasUsed());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
    }

    @Test
    void validation_rejectsBlockWhoseLastTxIsOneGasLargerThanTheGasLeftAfterTheRefund() {
        World world = newWorld(-1);
        Account authority = fundedAccount(world, "authority");
        Block parent = world.getBlockChain().getBestBlock();

        // A miner that does not apply the gas check publishes both txs
        Block block = blockWithoutMinerSelection(world, parent, delegation(world, authority), transfer(world, "other", 0, GAS_LEFT_AFTER_REFUND + 1));
        assertEquals(2, block.getTransactionsList().size());

        assertSame(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, validate(world, block, parent));
        assertEquals(ImportResult.INVALID_BLOCK, world.getBlockChain().tryToConnect(block));
        assertEquals(parent.getHash(), world.getBlockChain().getBestBlock().getHash());
    }

    @Test
    void parallelSublists_minerKeepsSameSenderTxInItsSublistWhenItFitsOnlyBecauseOfTheRefund() {
        World world = newWorld(0);
        Account authority = fundedAccount(world, "authority");
        long gasLeftInSublist = parallelSublistGasLimit(world) - TYPE4_GAS_USED_WITH_REFUND;

        Transaction tx2 = transfer(world, "sponsor", 1, gasLeftInSublist);
        Block block = mine(world, PARALLEL_BLOCK_GAS_LIMIT, List.of(delegation(world, authority), tx2));

        assertEquals(2, block.getTransactionsList().size());
        assertArrayEquals(new short[]{2}, block.getHeader().getTxExecutionSublistsEdges(),
                "both txs must share the first parallel sublist");
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
    }

    @Test
    void parallelSublists_sameSenderTxOneGasLargerMovesToTheSequentialSublist() {
        // With RSKIP-144 the miner does not drop the tx: it does not fit in the parallel sublist, so it goes to
        // the sequential sublist, which runs after the parallel ones and still has room.
        World world = newWorld(0);
        Account authority = fundedAccount(world, "authority");
        long gasLeftInSublist = parallelSublistGasLimit(world) - TYPE4_GAS_USED_WITH_REFUND;

        Transaction tx2 = transfer(world, "sponsor", 1, gasLeftInSublist + 1);
        Block block = mine(world, PARALLEL_BLOCK_GAS_LIMIT, List.of(delegation(world, authority), tx2));

        assertEquals(2, block.getTransactionsList().size());
        assertArrayEquals(new short[]{1}, block.getHeader().getTxExecutionSublistsEdges(),
                "only the type-4 tx stays in the parallel sublist");
        assertEquals(tx2.getHash(), block.getTransactionsList().get(1).getHash());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
    }

    private static long parallelSublistGasLimit(World world) {
        Block child = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .parent(world.getBlockChain().getBestBlock())
                .gasLimit(BigInteger.valueOf(PARALLEL_BLOCK_GAS_LIMIT))
                .buildWithoutExecution();
        long limit = BlockUtils.getSublistGasLimit(child, false, world.getConfig().getNetworkConstants().getMinSequentialSetGasLimit());
        assertTrue(limit > TYPE4_GAS_USED_WITH_REFUND, "the parallel sublists must have room for the type-4 tx");
        return limit;
    }

    private static Block mine(World world, List<Transaction> txs) {
        return mine(world, BLOCK_GAS_LIMIT, txs);
    }

    private static Block mine(World world, long blockGasLimit, List<Transaction> txs) {
        return new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(world.getBlockChain().getBestBlock())
                .gasLimit(BigInteger.valueOf(blockGasLimit))
                .transactions(txs)
                .build(world.getConfig());
    }

    /**
     * Builds the block with both txs and fills its header from an execution that keeps every tx, so the block
     * is what a miner that skips the gas check would publish.
     */
    private static Block blockWithoutMinerSelection(World world, Block parent, Transaction tx1, Transaction tx2) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .gasLimit(BigInteger.valueOf(BLOCK_GAS_LIMIT))
                .transactions(List.of(tx1, tx2))
                .buildWithoutExecution();
        world.getBlockExecutor().executeAndFillAll(block, parent.getHeader());
        return block;
    }

    private static BlockResult validate(World world, Block block, Block parent) {
        return world.getBlockExecutor().execute(null, 0, block, parent.getHeader(), false, false, false);
    }

    private static long gasUsed(World world, Block block, int txIndex) {
        BlockResult result = world.getBlockExecutor().execute(null, 0, block,
                world.getBlockChain().getBestBlock().getHeader(), false, false, false);
        List<TransactionReceipt> receipts = result.getTransactionReceipts();
        return ByteUtil.byteArrayToLong(receipts.get(txIndex).getGasUsed());
    }

    private static Transaction delegation(World world, Account authority) {
        Account sponsor = world.getAccountByName("sponsor");
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(BigInteger.ZERO)
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(200_000))
                .receiveAddress(sponsor.getAddress())
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(
                        authority.getEcKey(), Rskip545TestSupport.DEFAULT_DELEGATE, BigInteger.ZERO, CHAIN_ID)))
                .build();
        tx.sign(sponsor.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Transaction transfer(World world, String senderName, long nonce, long gasLimit) {
        Account sender = world.getAccountByName(senderName);
        RskAddress receiver = new RskAddress("0x00000000000000000000000000000000000000aa");
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(gasLimit))
                .receiveAddress(receiver)
                .value(Coin.valueOf(1))
                .chainId(CHAIN_ID)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Account fundedAccount(World world, String name) {
        return new AccountBuilder(world).name(name).balance(Coin.valueOf(1_000_000L)).build();
    }

    private static World newWorld(long rskip144Activation) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip544", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(rskip144Activation)));
        World world = new World(config);
        world.saveAccount("sponsor", new AccountBuilder(world).name("sponsor").balance(Coin.valueOf(1_000_000_000_000L)).build());
        world.saveAccount("other", new AccountBuilder(world).name("other").balance(Coin.valueOf(1_000_000_000_000L)).build());
        return world;
    }
}
