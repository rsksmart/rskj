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
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PreActivationTypedTxImportTest {

    private static final long BLOCK_HEIGHT = 1;
    private static final long NEXT_BLOCK = BLOCK_HEIGHT + 1;
    private static final RskAddress RECEIVER = new RskAddress("0x00000000000000000000000000000000000000aa");
    private static final RskAddress DELEGATE = new RskAddress("0x0000000000000000000000000000000000000003");
    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final short[] ONE_PARALLEL_SUBLIST = new short[]{1};

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void type1Tx_beforeRskip546_interruptsBlock(boolean parallel) {
        World world = newWorld(NEXT_BLOCK, NEXT_BLOCK, parallel);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, TransactionType.TYPE_1));
    }

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void type2Tx_beforeRskip546_interruptsBlock(boolean parallel) {
        World world = newWorld(NEXT_BLOCK, NEXT_BLOCK, parallel);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, TransactionType.TYPE_2));
    }

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void type4Tx_beforeRskip545_interruptsBlock(boolean parallel) {
        // RSKIP-543 and RSKIP-546 active: only the RSKIP-545 gate stands between the type-4 tx and execution
        World world = newWorld(BLOCK_HEIGHT, NEXT_BLOCK, parallel);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, TransactionType.TYPE_4));
    }

    @ParameterizedTest(name = "parallel={0}")
    @ValueSource(booleans = {false, true})
    void legacyTx_beforeActivation_doesNotInterruptBlock(boolean parallel) {
        World world = newWorld(NEXT_BLOCK, NEXT_BLOCK, parallel);

        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, TransactionType.LEGACY));
    }

    @ParameterizedTest(name = "{0}, parallel={1}")
    @MethodSource("typedTxsAndExecutionPaths")
    void typedTx_atActivation_doesNotInterruptBlock(TransactionType type, boolean parallel) {
        World world = newWorld(BLOCK_HEIGHT, BLOCK_HEIGHT, parallel);

        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, type));
    }

    static Stream<Arguments> typedTxsAndExecutionPaths() {
        return Stream.of(TransactionType.TYPE_1, TransactionType.TYPE_2, TransactionType.TYPE_4)
                .flatMap(type -> Stream.of(Arguments.of(type, false), Arguments.of(type, true)));
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("preActivationTypedTxs")
    void typedTx_beforeActivation_inParallelSublist_interruptsBlock(TransactionType type, long rskip543And546Activation, long rskip545Activation) {
        World world = newWorld(rskip543And546Activation, rskip545Activation, true);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, type, ONE_PARALLEL_SUBLIST));
    }

    @Test
    void legacyTx_beforeActivation_inParallelSublist_doesNotInterruptBlock() {
        World world = newWorld(NEXT_BLOCK, NEXT_BLOCK, true);

        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, executeAsValidation(world, TransactionType.LEGACY, ONE_PARALLEL_SUBLIST));
    }

    static Stream<Arguments> preActivationTypedTxs() {
        return Stream.of(
                Arguments.of(TransactionType.TYPE_1, NEXT_BLOCK, NEXT_BLOCK),
                Arguments.of(TransactionType.TYPE_2, NEXT_BLOCK, NEXT_BLOCK),
                // RSKIP-543 and RSKIP-546 active: only the RSKIP-545 gate stands between the type-4 tx and execution
                Arguments.of(TransactionType.TYPE_4, BLOCK_HEIGHT, NEXT_BLOCK));
    }

    private static BlockResult executeAsValidation(World world, TransactionType type) {
        return executeAsValidation(world, type, null);
    }

    private static BlockResult executeAsValidation(World world, TransactionType type, short[] sublistEdges) {
        Block genesis = world.getBlockChain().getBestBlock();
        Transaction tx = tx(type, world.getAccountByName("sender"), world.getAccountByName("authority"));
        BlockBuilder builder = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(genesis)
                .transactions(List.of(tx));
        if (sublistEdges != null) {
            builder.gasLimit(BigInteger.valueOf(40_000_000));
        }
        Block block = builder.buildWithoutExecution();
        if (sublistEdges != null) {
            block.getHeader().setTxExecutionSublistsEdges(sublistEdges);
        }

        assertEquals(BLOCK_HEIGHT, block.getNumber());
        assertEquals(List.of(tx), block.getTransactionsList(), "the block must still hold the tx");

        return world.getBlockExecutor().execute(null, 0, block, genesis.getHeader(), false, false, false);
    }

    private static Transaction tx(TransactionType type, Account sender, Account authority) {
        var builder = Transaction.builder().nonce(BigInteger.ZERO).gasLimit(BigInteger.valueOf(100_000)).receiveAddress(RECEIVER).value(Coin.valueOf(1));
        if (type == TransactionType.LEGACY) {
            builder.gasPrice(Coin.valueOf(1)).chainId(CHAIN_ID);
        } else {
            builder.type(type).chainId(CHAIN_ID).accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST);
            if (type == TransactionType.TYPE_1) {
                builder.gasPrice(Coin.valueOf(1));
            } else {
                builder.maxPriorityFeePerGas(Coin.valueOf(1)).maxFeePerGas(Coin.valueOf(1));
            }
        }
        if (type == TransactionType.TYPE_4) {
            builder.authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(authority.getEcKey(), DELEGATE, BigInteger.ZERO, CHAIN_ID)));
        }
        Transaction tx = builder.build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static World newWorld(long rskip543And546Activation, long rskip545Activation, boolean parallel) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(rskip543And546Activation))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(rskip543And546Activation))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(rskip545Activation))
                .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(parallel ? 0 : -1))
                .withValue("blockchain.config.consensusRules.rskip351", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sender", new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000_000L)).build());
        world.saveAccount("authority", new AccountBuilder(world).name("authority").build());
        return world;
    }
}
