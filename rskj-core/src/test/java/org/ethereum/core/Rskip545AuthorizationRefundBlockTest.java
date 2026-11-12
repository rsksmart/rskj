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
package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * RSKIP-545 authorization refund (EIP-161 emptiness) at block level: state set by an earlier
 * transaction of the same block, and block validation against a header without the refund.
 */
class Rskip545AuthorizationRefundBlockTest {

    private static final long SINGLE_AUTH_INTRINSIC = GasCost.TRANSACTION + GasCost.PER_EMPTY_ACCOUNT_COST;
    private static final long AUTHORIZATION_REFUND = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;

    // Header fields captured from a build that withholds the first-delegation refund of a funded EOA (46_000 gas, no refund)
    private static final byte[] NO_REFUND_STATE_ROOT = Hex.decode("f0c9c9523d3ebdca98c5fdae46754d17ff12417ffb0b5ac7d67e3912362d65c1");
    private static final byte[] NO_REFUND_RECEIPTS_ROOT = Hex.decode("f53c3c8dc7c2c23df8ac23c3b837139bdf9da0f6e3970b22eefc2bb1703b17a4");
    private static final long NO_REFUND_GAS_USED = 46_000L;

    private static World world;

    @BeforeAll
    static void setup() throws FileNotFoundException, DslProcessorException {
        world = new World(rskip545Config());
        world.saveAccount("fresh", new Account(ECKey.fromPrivate(HashUtil.keccak256("fresh".getBytes()))));

        DslParser parser = DslParser.fromResource("dsl/transaction/rskip545/rskip545AuthorizationRefundTest.txt");
        new WorldDslProcessor(world).processCommands(parser);
    }

    @Test
    void firstDelegationOfAuthorityFundedEarlierInTheBlock_isRefunded() {
        RskAddress fresh = world.getAccountByName("fresh").getAddress();

        assertFalse(repoAt("g00").isExist(fresh), "The authority must not exist before the block");
        assertEquals(Coin.valueOf(1), repoAt("b01").getBalance(fresh));
        assertEquals(SINGLE_AUTH_INTRINSIC - AUTHORIZATION_REFUND, gasUsedInBlock("b01", "txDelegateFresh"),
                "The authority funded by the previous transaction is not EIP-161 empty");
    }

    @Test
    void blockWithoutTheFirstDelegationRefundOfAFundedEoa_isRejected() {
        World blockWorld = new World(rskip545Config());
        Account sender = new AccountBuilder(blockWorld).name("sender").balance(Coin.valueOf(1_000_000_000L)).build();
        Account authority = new AccountBuilder(blockWorld).name("authority").balance(Coin.valueOf(1_000_000L)).build();
        Account receiver = new AccountBuilder(blockWorld).name("receiver").build();

        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(Rskip545TestSupport.REGTEST_CHAIN_ID)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(100_000))
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .receiveAddress(receiver.getAddress())
                .value(Coin.ZERO)
                .data(new byte[0])
                .authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(
                        authority.getEcKey(), Rskip545TestSupport.DEFAULT_DELEGATE, BigInteger.ZERO, Rskip545TestSupport.REGTEST_CHAIN_ID)))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        Block parent = blockWorld.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(blockWorld.getBlockChain(), blockWorld.getBridgeSupportFactory(), blockWorld.getBlockStore())
                .trieStore(blockWorld.getTrieStore())
                .parent(parent)
                .transactions(Collections.singletonList(tx))
                .build(blockWorld.getConfig());
        long computedGasUsed = block.getGasUsed();

        block.getHeader().setStateRoot(NO_REFUND_STATE_ROOT);
        block.getHeader().setReceiptsRoot(NO_REFUND_RECEIPTS_ROOT);
        block.getHeader().setGasUsed(NO_REFUND_GAS_USED);
        block.getHeader().setPaidFees(Coin.valueOf(NO_REFUND_GAS_USED));

        assertFalse(blockWorld.getBlockExecutor().executeAndValidate(block, parent.getHeader()),
                "A header without the refund must be rejected");
        assertEquals(SINGLE_AUTH_INTRINSIC - AUTHORIZATION_REFUND, computedGasUsed);
    }

    private static TestSystemProperties rskip545Config() {
        return new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0)));
    }

    private static RepositorySnapshot repoAt(String blockName) {
        return world.getRepositoryLocator().snapshotAt(world.getBlockByName(blockName).getHeader());
    }

    private static long gasUsedInBlock(String blockName, String txName) {
        Block block = world.getBlockByName(blockName);
        Transaction target = world.getTransactionByName(txName);
        long prevCumulative = 0;
        for (Transaction tx : block.getTransactionsList()) {
            TransactionInfo info = world.getReceiptStore()
                    .get(tx.getHash().getBytes(), block.getHash().getBytes())
                    .orElseThrow(() -> new IllegalStateException("Missing receipt for " + tx.getHash()));
            long cumulative = info.getReceipt().getCumulativeGasLong();
            if (tx.getHash().equals(target.getHash())) {
                return cumulative - prevCumulative;
            }
            prevCumulative = cumulative;
        }
        throw new IllegalStateException("Transaction " + txName + " not found in block " + blockName);
    }
}
