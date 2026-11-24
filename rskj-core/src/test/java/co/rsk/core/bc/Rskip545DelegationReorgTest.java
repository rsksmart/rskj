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
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A reorg that drops the block holding a delegation change must give the authority back the code, code hash,
 * nonce and delegation flag it had before that block. Switching back to the first chain must bring the change
 * back.
 */
class Rskip545DelegationReorgTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final Coin BALANCE = Coin.valueOf(1_000_000_000_000_000L);
    private static final RskAddress RECEIVER = new RskAddress("0000000000000000000000000000000000001234");
    // slot0 += 1
    private static final byte[] COUNTER = Hex.decode("60005460010160005500");
    // slot0 = 0x22
    private static final byte[] STORE_22 = Hex.decode("602260005500");

    enum Change {
        // the authority had no code; the dropped block delegates it to COUNTER
        SET,
        // the authority was delegated to COUNTER; the dropped block clears it
        CLEAR,
        // the authority was delegated to COUNTER; the dropped block re-delegates it to STORE_22
        REDELEGATE
    }

    /**
     * Chain: base (with the "before" state) -> N (a sponsor type-4 changes the authority's delegation).
     * Fork: base -> F1 -> F2, no transactions, so the fork is longer and becomes best.
     * Expected: on the fork, the authority's code, code hash, nonce and delegation flag equal the state at base.
     * Then N -> N2 -> N3 makes the first chain best again, and the state after N comes back.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(Change.class)
    void reorgDroppingDelegationBlock_restoresAuthorityState_andSwitchingBackRestoresTheChange(Change change) {
        World world = newWorld();
        Account sponsor = world.getAccountByName("sponsor");
        Account authority = world.getAccountByName("authority");
        RskAddress counter = world.getAccountByName("counter").getAddress();
        RskAddress store22 = world.getAccountByName("store22").getAddress();
        RskAddress a = authority.getAddress();

        if (change != Change.SET) {
            connectBest(world, delegation(world, sponsor, authority, counter));
        }
        Block base = world.getBlockChain().getBestBlock();
        AuthorityState before = AuthorityState.read(Rskip545TestSupport.snapshotAtBest(world), a);

        RskAddress newDelegate = switch (change) {
            case SET -> counter;
            case CLEAR -> RskAddress.ZERO_ADDRESS;
            case REDELEGATE -> store22;
        };
        Block n = connectBest(world, delegation(world, sponsor, authority, newDelegate));
        AuthorityState afterN = AuthorityState.read(Rskip545TestSupport.snapshotAtBest(world), a);

        byte[] expectedCodeAfterN = change == Change.CLEAR
                ? ByteUtil.EMPTY_BYTE_ARRAY
                : DelegationCodeResolver.createDelegatedCode(newDelegate);
        assertArrayEquals(expectedCodeAfterN, afterN.code, "block N must change the authority's code");
        assertEquals(before.nonce.add(BigInteger.ONE), afterN.nonce, "the tuple must bump the authority's nonce");
        assertNotEquals(before, afterN, "block N must change the authority's state");

        Block f1 = emptyChild(world, base);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(f1), "F1 alone is not heavier than N");
        Block f2 = emptyChild(world, f1);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(f2), "the longer fork must become best");
        assertEquals(f2.getHash(), world.getBlockChain().getBestBlock().getHash());

        AuthorityState onFork = AuthorityState.read(Rskip545TestSupport.snapshotAtBest(world), a);
        assertEquals(before, onFork, "on the fork without N, the authority must be back to its state before N");

        Block n2 = emptyChild(world, n);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(n2), "N2 only ties the fork");
        Block n3 = emptyChild(world, n2);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(n3), "the first chain must become best again");

        AuthorityState backOnFirstChain = AuthorityState.read(Rskip545TestSupport.snapshotAtBest(world), a);
        assertEquals(afterN, backOnFirstChain, "switching back must bring back the state N set");
        assertEquals(n.getHash(), world.getBlockChain().getBlockByNumber(n.getNumber()).getHash(),
                "the main chain at N's height must be N again");
    }

    private record AuthorityState(byte[] code, Keccak256 codeHash, BigInteger nonce, boolean delegationFlag, DataWord slot0) {
        static AuthorityState read(RepositorySnapshot state, RskAddress address) {
            byte[] code = state.getCode(address);
            DataWord slot0 = state.getStorageValue(address, DataWord.ZERO);
            return new AuthorityState(
                    code == null ? ByteUtil.EMPTY_BYTE_ARRAY : code,
                    state.getCodeHashStandard(address),
                    state.getNonce(address),
                    state.hasDelegationAuthority(address),
                    slot0 == null ? DataWord.ZERO : slot0);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AuthorityState other)) {
                return false;
            }
            return java.util.Arrays.equals(code, other.code) && codeHash.equals(other.codeHash)
                    && nonce.equals(other.nonce) && delegationFlag == other.delegationFlag && slot0.equals(other.slot0);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(java.util.Arrays.hashCode(code), codeHash, nonce, delegationFlag, slot0);
        }

        @Override
        public String toString() {
            return "code=" + Hex.toHexString(code) + " codeHash=" + codeHash + " nonce=" + nonce
                    + " delegationFlag=" + delegationFlag + " slot0=" + slot0;
        }
    }

    private static Transaction delegation(World world, Account sponsor, Account authority, RskAddress delegate) {
        RepositorySnapshot state = Rskip545TestSupport.snapshotAtBest(world);
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(state.getNonce(sponsor.getAddress()))
                .maxPriorityFeePerGas(Coin.valueOf(10))
                .maxFeePerGas(Coin.valueOf(10))
                .gasLimit(BigInteger.valueOf(500_000))
                .receiveAddress(RECEIVER)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(
                        authority.getEcKey(), delegate, state.getNonce(authority.getAddress()), CHAIN_ID)))
                .build();
        tx.sign(sponsor.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Block connectBest(World world, Transaction tx) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(world.getBlockChain().getBestBlock())
                .transactions(Collections.singletonList(tx))
                .build(world.getConfig());
        assertEquals(1, block.getTransactionsList().size(), "execution must keep the transaction");
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
        return block;
    }

    private static Block emptyChild(World world, Block parent) {
        return new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(Collections.emptyList())
                .build(world.getConfig());
    }

    private static World newWorld() {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sponsor", new AccountBuilder(world).name("sponsor").balance(BALANCE).build());
        world.saveAccount("authority", new AccountBuilder(world).name("authority").balance(BALANCE).build());
        world.saveAccount("counter", new AccountBuilder(world).name("counter").code(COUNTER).build());
        world.saveAccount("store22", new AccountBuilder(world).name("store22").code(STORE_22).build());
        return world;
    }
}
