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
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSKIP-545 / EIP-7702 transaction-origin invariant tests.
 *
 * <p>Self-sponsoring (authority sets code on itself and executes delegated bytecode) breaks the
 * pre-7702 assumption that {@code msg.sender == tx.origin} only occurs in the topmost execution
 * frame. Contracts using {@code require(msg.sender == tx.origin)} for sandwich or reentrancy
 * <p>See also {@link Rskip545OriginInvariantDslTest} (DSL integration + gas schedule).
 */
class Rskip545OriginInvariantTest {

    private static final byte CHAIN_ID = 33;
    private static final byte[] USER_PK = HashUtil.keccak256("originInvariantUser".getBytes());
    private static final ECKey USER_KEY = ECKey.fromPrivate(USER_PK);
    private static final Coin GAS_PRICE = Coin.valueOf(1_000_000_000L);
    private static final long TYPE4_GAS = 500_000L;
    private static final long DEPLOY_GAS = 200_000L;

    /**
     * When called, stores {@code (tx.origin == msg.sender) ? 1 : 0} in slot 2.
     */
    private static final byte[] ORIGIN_SENDER_GUARD =
            Hex.decode("32231460025500");

    /** Probe contract: slot 0 = {@code tx.origin}, slot 1 = {@code msg.sender}. */
    private static final byte[] ORIGIN_CALLER_PROBE =
            Hex.decode("326000553360015500");

    private World world;
    private Account user;
    private BigInteger nextDeployNonce;

    @BeforeEach
    void setup() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
        );
        world = new World(config);
        user = new AccountBuilder(world)
                .name("originInvariantUser")
                .balance(Coin.valueOf(10).multiply(BigInteger.valueOf(1_000_000_000_000_000_000L)))
                .build();
        assertEquals(new RskAddress(USER_KEY.getAddress()), user.getAddress());
        nextDeployNonce = BigInteger.ZERO;
    }

    @Test
    void selfSponsoredType4_originEqualsCallerAtTopFrame() {
        RskAddress delegateImpl = deployRuntime(ORIGIN_CALLER_PROBE);

        Block block = mineSelfSponsoredType4(delegateImpl, new byte[0]);

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());
        RepositorySnapshot repo = snapshot(block);
        DataWord storedOrigin = repo.getStorageValue(user.getAddress(), DataWord.ZERO);
        DataWord storedCaller = repo.getStorageValue(user.getAddress(), DataWord.ONE);
        assertEquals(DataWord.valueOf(user.getAddress().getBytes()), storedOrigin,
                "Delegated self-sponsored top frame must preserve tx.origin");
        assertEquals(DataWord.valueOf(user.getAddress().getBytes()), storedCaller,
                "Delegated self-sponsored top frame must set msg.sender to the authority");
        assertEquals(storedOrigin, storedCaller,
                "msg.sender == tx.origin must hold while executing delegated bytecode in the top frame");
    }

    @Test
    void selfSponsoredType4_nestedCall_originEqualsCallerInChildFrame() {
        RskAddress probe = deployRuntime(ORIGIN_CALLER_PROBE);
        RskAddress delegateImpl = deployRuntime(callRuntime(probe));

        Block block = mineSelfSponsoredType4(delegateImpl, new byte[0]);

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());
        RepositorySnapshot repo = snapshot(block);
        DataWord storedOrigin = repo.getStorageValue(probe, DataWord.ZERO);
        DataWord storedCaller = repo.getStorageValue(probe, DataWord.ONE);
        assertEquals(DataWord.valueOf(user.getAddress().getBytes()), storedOrigin,
                "Nested CALL must preserve tx.origin");
        assertEquals(DataWord.valueOf(user.getAddress().getBytes()), storedCaller,
                "Nested CALL from delegated EOA must set msg.sender to the authority");
        assertEquals(storedOrigin, storedCaller,
                "msg.sender == tx.origin must hold in nested frames when delegated EOA issues CALL; "
                        + "a require(msg.sender == tx.origin) guard would therefore pass here");
    }

    @Test
    void guardContract_rejectsContractRelay() {
        RskAddress guard = deployRuntime(ORIGIN_SENDER_GUARD);
        RskAddress relay = deployRuntime(callRuntime(guard));

        Transaction relayTx = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(nextDeployNonce)
                .gasLimit(BigInteger.valueOf(DEPLOY_GAS))
                .maxPriorityFeePerGas(GAS_PRICE)
                .maxFeePerGas(GAS_PRICE.multiply(BigInteger.valueOf(2)))
                .receiveAddress(relay)
                .data(new byte[0])
                .value(Coin.ZERO)
                .build();
        relayTx.sign(USER_PK);

        Block block = mine(world.getBlockChain().getBestBlock(), List.of(relayTx));

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus(),
                "Relay bytecode ignores CALL failure; outer tx still succeeds");
        DataWord guardSlot = snapshot(block).getStorageValue(guard, DataWord.valueOf(2));
        assertTrue(guardSlot == null || guardSlot.isZero(),
                "Guard must record failure when msg.sender (relay) != tx.origin (EOA)");
        assertNotEquals(DataWord.ONE, guardSlot);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RskAddress deployRuntime(byte[] runtime) {
        byte[] init = initCodeReturning(runtime);
        BigInteger nonce = nextDeployNonce;
        nextDeployNonce = nextDeployNonce.add(BigInteger.ONE);

        Transaction deploy = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(nonce)
                .gasLimit(BigInteger.valueOf(DEPLOY_GAS))
                .maxPriorityFeePerGas(GAS_PRICE)
                .maxFeePerGas(GAS_PRICE.multiply(BigInteger.valueOf(2)))
                .data(init)
                .value(Coin.ZERO)
                .build();
        deploy.sign(USER_PK);

        Block parent = world.getBlockChain().getBestBlock();
        Block block = mine(parent, List.of(deploy));
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());

        RskAddress deployed = new RskAddress(HashUtil.calcNewAddr(USER_KEY.getAddress(), nonce.toByteArray()));
        assertTrue(snapshot(block).getCode(deployed).length > 0, "Contract must be deployed at " + deployed);
        return deployed;
    }

    private static byte[] initCodeReturning(byte[] runtime) {
        if (runtime.length > 255) {
            throw new IllegalArgumentException("Test runtime must fit in one-byte PUSH1 length");
        }
        int offset = 12;
        byte[] init = new byte[offset + runtime.length];
        init[0] = 0x60;
        init[1] = (byte) runtime.length;
        init[2] = 0x60;
        init[3] = (byte) offset;
        init[4] = 0x60;
        init[5] = 0x00;
        init[6] = 0x39;
        init[7] = 0x60;
        init[8] = (byte) runtime.length;
        init[9] = 0x60;
        init[10] = 0x00;
        init[11] = (byte) 0xf3;
        System.arraycopy(runtime, 0, init, offset, runtime.length);
        return init;
    }

    private static byte[] callRuntime(RskAddress target) {
        return Hex.decode(
                "6000600060006000600073"
                        + Hex.toHexString(target.getBytes())
                        + "5af100"
        );
    }

    private Block mineSelfSponsoredType4(RskAddress delegateImpl, byte[] data) {
        BigInteger outerNonce = snapshot(world.getBlockChain().getBestBlock()).getNonce(user.getAddress());
        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(
                USER_KEY, delegateImpl, outerNonce.add(BigInteger.ONE), CHAIN_ID);

        Transaction type4 = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(CHAIN_ID)
                .nonce(outerNonce)
                .gasLimit(BigInteger.valueOf(TYPE4_GAS))
                .maxPriorityFeePerGas(GAS_PRICE)
                .maxFeePerGas(GAS_PRICE.multiply(BigInteger.valueOf(2)))
                .receiveAddress(user.getAddress())
                .data(data)
                .value(Coin.ZERO)
                .authorizationList(List.of(auth))
                .build();
        type4.sign(USER_PK);

        return mine(world.getBlockChain().getBestBlock(), List.of(type4));
    }

    private Block mine(Block parent, List<Transaction> txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(txs)
                .build();
        ImportResult result = world.getBlockChain().tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        return block;
    }

    private TransactionReceipt receipt(Block block, int index) {
        Transaction tx = block.getTransactionsList().get(index);
        return world.getReceiptStore()
                .getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                .orElseThrow()
                .getReceipt();
    }

    private RepositorySnapshot snapshot(Block block) {
        return world.getRepositoryLocator().snapshotAt(block.getHeader());
    }
}
