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
import org.ethereum.core.transaction.TransactionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block mined by a node where the typed transaction rules are active is imported by nodes that put the
 * activation at the block's height (it imports) or one block later (it is rejected), through both the
 * sequential (RSKIP-144 off) and the parallel (RSKIP-144 on) block execution paths.
 */
class TypedTxBlockImportActivationTest {

    private static final long BLOCK_HEIGHT = 1;
    private static final RskAddress DELEGATE = new RskAddress("0x0000000000000000000000000000000000000003");
    private static final RskAddress RECEIVER = new RskAddress("0x00000000000000000000000000000000000000aa");
    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;

    static Stream<Arguments> typesAndExecutionPaths() {
        return Stream.of(TransactionType.TYPE_1, TransactionType.TYPE_2, TransactionType.TYPE_4)
                .flatMap(type -> Stream.of(Arguments.of(type, true), Arguments.of(type, false)));
    }

    @ParameterizedTest(name = "{0}, parallel={1}")
    @MethodSource("typesAndExecutionPaths")
    void typedTxBlock_isRejectedOneBlockBeforeActivation(TransactionType type, boolean parallel) {
        Block block = mineBlockWithTypedTx(type, parallel);

        World importer = newWorld(BLOCK_HEIGHT + 1, parallel);
        Block importerGenesis = importer.getBlockChain().getBestBlock();
        assertEquals(block.getParentHash(), importerGenesis.getHash(), "both worlds must share the genesis");

        assertEquals(ImportResult.INVALID_BLOCK, importer.getBlockChain().tryToConnect(block));
        assertEquals(importerGenesis.getHash(), importer.getBlockChain().getBestBlock().getHash());
    }

    @ParameterizedTest(name = "{0}, parallel={1}")
    @MethodSource("typesAndExecutionPaths")
    void typedTxBlock_importsAtActivation(TransactionType type, boolean parallel) {
        Block block = mineBlockWithTypedTx(type, parallel);

        World importer = newWorld(BLOCK_HEIGHT, parallel);
        assertEquals(block.getParentHash(), importer.getBlockChain().getBestBlock().getHash(), "both worlds must share the genesis");

        assertEquals(ImportResult.IMPORTED_BEST, importer.getBlockChain().tryToConnect(block));
        assertEquals(block.getHash(), importer.getBlockChain().getBestBlock().getHash());
    }

    private static Block mineBlockWithTypedTx(TransactionType type, boolean parallel) {
        World miner = newWorld(BLOCK_HEIGHT, parallel);
        Account sender = miner.getAccountByName("sender");
        Account authority = miner.getAccountByName("authority");

        Transaction tx = typedTx(type, sender, authority);
        Block genesis = miner.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(miner.getBlockChain(), miner.getBridgeSupportFactory(), miner.getBlockStore())
                .trieStore(miner.getTrieStore())
                .parent(genesis)
                .transactions(Collections.singletonList(tx))
                .build(miner.getConfig());

        assertEquals(BLOCK_HEIGHT, block.getNumber());
        assertEquals(1, block.getTransactionsList().size(), "the miner must keep the typed tx");
        assertEquals(type, block.getTransactionsList().get(0).getType());
        assertEquals(parallel, block.getHeader().getTxExecutionSublistsEdges() != null, "the block must come from the expected execution path");
        return block;
    }

    private static Transaction typedTx(TransactionType type, Account sender, Account authority) {
        var builder = Transaction.builder()
                .type(type)
                .nonce(BigInteger.valueOf(0))
                .gasLimit(BigInteger.valueOf(100_000))
                // Not the authority: with RSKIP-545 off the authority's designator would run as code and reject
                // the block for a reason other than the type gate this test pins
                .receiveAddress(RECEIVER)
                .value(Coin.valueOf(1))
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST);
        if (type == TransactionType.TYPE_1) {
            builder.gasPrice(Coin.valueOf(1));
        } else {
            builder.maxPriorityFeePerGas(Coin.valueOf(1)).maxFeePerGas(Coin.valueOf(1));
        }
        if (type == TransactionType.TYPE_4) {
            builder.authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(
                    authority.getEcKey(), DELEGATE, BigInteger.valueOf(0), CHAIN_ID)));
        }
        Transaction tx = builder.build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static World newWorld(long typedTxActivation, boolean parallel) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(typedTxActivation))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(typedTxActivation))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(typedTxActivation))
                .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(parallel ? 0 : -1))
                .withValue("blockchain.config.consensusRules.rskip351", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sender", new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000_000L)).build());
        world.saveAccount("authority", new AccountBuilder(world).name("authority").build());
        return world;
    }
}
