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
import co.rsk.db.RepositorySnapshot;
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
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Collections;

import static org.ethereum.core.Rskip545TestSupport.initCodeReturning;
import static org.ethereum.core.Rskip545TestSupport.snapshotAtBest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A legacy call to a contract that runs SELFDESTRUCT earns a refund larger than half of the gas used, so the refund
 * cap applies. The expected figures are the legacy refund-cap values, and they must hold with the account
 * abstraction rules off and on.
 */
class LegacySelfdestructRefundCapTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    // CALLER, SELFDESTRUCT
    private static final byte[] SELFDESTRUCT_RUNTIME = Hex.decode("33ff");
    private static final long CONTRACT_BALANCE = 1_000;
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(2);
    // 26_002 gas before refunds (21_000 + CALLER 2 + SELFDESTRUCT 5_000), refund min(24_000, 26_002 / 2)
    private static final long EXPECTED_GAS_USED = 13_001L;

    @ParameterizedTest(name = "accountAbstractionActive={0}")
    @ValueSource(booleans = {false, true})
    void selfdestructRefundAboveTheCap_isCappedAtHalfTheGasUsed(boolean accountAbstractionActive) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.hardforkActivationHeights.cardamom1000",
                        ConfigValueFactory.fromAnyRef(accountAbstractionActive ? 0 : -1)));
        World world = new World(config);
        Account sender = new AccountBuilder(world).name("sender").balance(Coin.valueOf(1_000_000_000L)).build();

        RskAddress contract = deploy(world, sender, SELFDESTRUCT_RUNTIME);
        Coin balanceBeforeCall = snapshotAtBest(world).getBalance(sender.getAddress());

        Transaction call = Transaction.builder()
                .nonce(BigInteger.valueOf(1))
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(100_000))
                .receiveAddress(contract)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .build();
        call.sign(sender.getEcKey().getPrivKeyBytes());
        Block block = connect(world, call);

        TransactionReceipt receipt = world.getReceiptStore()
                .getInMainChain(call.getHash().getBytes(), world.getBlockStore()).orElseThrow().getReceipt();
        long gasUsed = new BigInteger(1, receipt.getGasUsed()).longValue();
        RepositorySnapshot state = snapshotAtBest(world);

        assertTrue(receipt.isSuccessful());
        assertEquals(EXPECTED_GAS_USED, gasUsed);
        assertEquals(EXPECTED_GAS_USED, block.getGasUsed());
        assertEquals(balanceBeforeCall.subtract(Coin.valueOf(EXPECTED_GAS_USED * GAS_PRICE.longValue())).add(Coin.valueOf(CONTRACT_BALANCE)),
                state.getBalance(sender.getAddress()));
        assertTrue(state.getCode(contract) == null || state.getCode(contract).length == 0, "the contract must be deleted");
    }

    private static RskAddress deploy(World world, Account sender, byte[] runtime) {
        Transaction create = Transaction.builder()
                .nonce(BigInteger.valueOf(0))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(300_000))
                .value(Coin.valueOf(CONTRACT_BALANCE))
                .chainId(CHAIN_ID)
                .data(initCodeReturning(runtime))
                .build();
        create.sign(sender.getEcKey().getPrivKeyBytes());
        connect(world, create);
        return new RskAddress(HashUtil.calcNewAddr(sender.getAddress().getBytes(), BigInteger.valueOf(0).toByteArray()));
    }

    private static Block connect(World world, Transaction tx) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(world.getBlockChain().getBestBlock())
                .transactions(Collections.singletonList(tx))
                .build(world.getConfig());
        assertEquals(1, block.getTransactionsList().size());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
        return block;
    }
}
