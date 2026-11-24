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
package co.rsk.core.parallel;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockResult;
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
import org.ethereum.core.TransactionBuilder;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A delegation change, or a change to the delegate's code, made by one transaction must be seen by later
 * transactions in the same block when the block runs on the parallel path (RSKIP-144), exactly as on the
 * sequential path.
 *
 * <p>Two kinds of checks:
 * <ul>
 *     <li>an honest miner builds the block with RSKIP-144 on and off: both nodes import it, the later
 *     transactions see the new state, and the state roots match;</li>
 *     <li>a crafted block puts the change and a later reader in different parallel sublists: the follower must
 *     detect the collision and stop, instead of letting the reader see the old code.</li>
 * </ul>
 */
class Rskip545ParallelDelegationVisibilityTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final Coin BALANCE = Coin.valueOf(1_000_000_000_000_000L);
    private static final RskAddress RECEIVER = new RskAddress("0000000000000000000000000000000000001234");
    private static final RskAddress UNRELATED = new RskAddress("000000000000000000000000000000000000cccc");

    // slot0 += 1
    private static final byte[] COUNTER = Hex.decode("60005460010160005500");
    // slot0 = 0x22
    private static final byte[] STORE_22 = Hex.decode("602260005500");
    // With call data: SELFDESTRUCT(0). Without call data: slot0 += 1.
    // CALLDATASIZE ISZERO PUSH1 8 JUMPI PUSH1 0 SELFDESTRUCT JUMPDEST PUSH1 0 SLOAD PUSH1 1 ADD PUSH1 0 SSTORE STOP
    private static final byte[] DESTRUCTIBLE_COUNTER = Hex.decode("36156008576000ff5b60005460010160005500");

    private static final DataWord SLOT0_AFTER_FIRST_COUNTER_CALL = DataWord.ONE;
    private static final DataWord SLOT0_IF_OLD_COUNTER_RAN_AGAIN = DataWord.valueOf(2);
    private static final DataWord SLOT0_AFTER_STORE_22 = DataWord.valueOf(0x22);

    enum Change {
        // tx1 re-delegates the authority from COUNTER to STORE_22
        REDELEGATE,
        // tx1 clears the authority's delegation
        CLEAR
    }

    /**
     * Setup: authority A delegated to COUNTER and called once (A.slot0 = 1).
     * Block (honest miner): tx1 sponsor type-4 changes A (re-delegate to STORE_22, or clear); tx2 sponsor2 calls A;
     * tx3 sponsor3 calls a probe that stores EXTCODEHASH(A).
     * Expected, with RSKIP-144 on and off: tx2 and tx3 see the state after tx1, and both state roots are equal.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(Change.class)
    void honestMiner_delegationChangeThenCallThenExtcodehash_laterTxsSeeTheChange_sameStateRootAsSequential(Change change) {
        Chain sequential = new Chain(-1);
        Chain parallel = new Chain(0);

        for (Chain chain : List.of(sequential, parallel)) {
            chain.delegate(chain.counter);
            chain.connect(call(chain.sponsor2, 0, chain.authority.getAddress(), null));
            assertEquals(SLOT0_AFTER_FIRST_COUNTER_CALL, chain.storage(chain.authority.getAddress()));
        }

        RskAddress newDelegate = change == Change.REDELEGATE ? sequential.store22 : RskAddress.ZERO_ADDRESS;
        Transaction tx1 = type4(sequential.sponsor, sequential.nonceOf(sequential.sponsor), RECEIVER,
                tuple(sequential.authority, newDelegate, sequential.nonceOf(sequential.authority)));
        Transaction tx2 = call(sequential.sponsor2, 1, sequential.authority.getAddress(), null);
        Transaction tx3 = call(sequential.sponsor3, 0, sequential.probe, null);

        Block sequentialBlock = sequential.connect(tx1, tx2, tx3);
        Block parallelBlock = parallel.connect(tx1, tx2, tx3);

        byte[] expectedCode = change == Change.REDELEGATE
                ? DelegationCodeResolver.createDelegatedCode(sequential.store22)
                : ByteUtil.EMPTY_BYTE_ARRAY;
        DataWord expectedSlot0 = change == Change.REDELEGATE ? SLOT0_AFTER_STORE_22 : SLOT0_AFTER_FIRST_COUNTER_CALL;

        for (Chain chain : List.of(sequential, parallel)) {
            RepositorySnapshot state = chain.state();
            byte[] code = state.getCode(chain.authority.getAddress());
            assertArrayEquals(expectedCode, code == null ? ByteUtil.EMPTY_BYTE_ARRAY : code, "tx1 must have changed the authority's code");
            assertEquals(expectedSlot0, chain.storage(chain.authority.getAddress()),
                    "tx2 must run the code tx1 left behind, never the old COUNTER (which would give 2)");
            assertNotEquals(SLOT0_IF_OLD_COUNTER_RAN_AGAIN, chain.storage(chain.authority.getAddress()));
            assertArrayEquals(HashUtil.keccak256(expectedCode), chain.storage(chain.probe).getData(),
                    "tx3 must read the code hash tx1 left behind");
        }

        assertArrayEquals(sequentialBlock.getHeader().getStateRoot(), parallelBlock.getHeader().getStateRoot(),
                "the parallel and sequential paths must reach the same state root");
        assertDependentTxsNotInAnotherParallelSublist(parallelBlock.getHeader().getTxExecutionSublistsEdges(), 0, 1, 2);
    }

    /**
     * Setup: authority A delegated to DESTRUCTIBLE_COUNTER (D3) and called once (A.slot0 = 1).
     * Block (honest miner): tx1 sponsor calls D3 with call data, so D3 self-destructs; tx2 sponsor2 calls A.
     * Expected, with RSKIP-144 on and off: tx2 sees that D3 has no code any more, so A.slot0 stays 1, and both
     * state roots are equal.
     */
    @Test
    void honestMiner_delegateSelfDestructsThenDelegatedCall_laterTxSeesNoDelegateCode_sameStateRootAsSequential() {
        Chain sequential = new Chain(-1);
        Chain parallel = new Chain(0);

        for (Chain chain : List.of(sequential, parallel)) {
            chain.delegate(chain.destructible);
            chain.connect(call(chain.sponsor2, 0, chain.authority.getAddress(), null));
            assertEquals(SLOT0_AFTER_FIRST_COUNTER_CALL, chain.storage(chain.authority.getAddress()));
        }

        Transaction tx1 = call(sequential.sponsor, sequential.nonceOf(sequential.sponsor), sequential.destructible, new byte[]{0x01});
        Transaction tx2 = call(sequential.sponsor2, 1, sequential.authority.getAddress(), null);

        Block sequentialBlock = sequential.connect(tx1, tx2);
        Block parallelBlock = parallel.connect(tx1, tx2);

        for (Chain chain : List.of(sequential, parallel)) {
            RepositorySnapshot state = chain.state();
            byte[] delegateCode = state.getCode(chain.destructible);
            assertEquals(0, delegateCode == null ? 0 : delegateCode.length, "tx1 must have removed the delegate's code");
            assertArrayEquals(DelegationCodeResolver.createDelegatedCode(chain.destructible), state.getCode(chain.authority.getAddress()),
                    "the designator itself stays on the authority");
            assertEquals(SLOT0_AFTER_FIRST_COUNTER_CALL, chain.storage(chain.authority.getAddress()),
                    "tx2 must find no delegate code; running the old code would give 2");
        }

        assertArrayEquals(sequentialBlock.getHeader().getStateRoot(), parallelBlock.getHeader().getStateRoot(),
                "the parallel and sequential paths must reach the same state root");
        assertDependentTxsNotInAnotherParallelSublist(parallelBlock.getHeader().getTxExecutionSublistsEdges(), 0, 1);
    }

    enum Reader {
        // sponsor2 calls the authority, which runs its delegate's code
        CALL_AUTHORITY,
        // sponsor3 calls a probe that reads EXTCODEHASH(authority) and writes only its own storage
        EXTCODEHASH_PROBE
    }

    /**
     * Setup: RSKIP-144 on; authority A delegated to COUNTER.
     * Block (crafted, not executed by a miner), sublist edges [1, 2]:
     *   sublist 1: sponsor type-4 re-delegates A to STORE_22
     *   sublist 2: a tx that reads A's code (a call to A, or EXTCODEHASH(A) from a probe)
     * Expected: the follower's parallel execution detects the collision and stops the block.
     * Control: the same layout with an unrelated tx in sublist 2 executes.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(Reader.class)
    void craftedBlock_redelegationAndCodeReaderInDifferentSublists_collisionIsDetected(Reader reader) {
        Chain chain = new Chain(0);
        chain.delegate(chain.counter);

        Transaction redelegation = type4(chain.sponsor, chain.nonceOf(chain.sponsor), RECEIVER,
                tuple(chain.authority, chain.store22, chain.nonceOf(chain.authority)));
        Transaction codeReader = reader == Reader.CALL_AUTHORITY
                ? call(chain.sponsor2, 0, chain.authority.getAddress(), null)
                : call(chain.sponsor3, 0, chain.probe, null);
        Transaction independent = call(chain.sponsor4, 0, UNRELATED, null);

        Block conflicting = chain.unexecutedBlockWithSublists(new short[]{1, 2}, redelegation, codeReader);
        Block control = chain.unexecutedBlockWithSublists(new short[]{1, 2}, redelegation, independent);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, chain.executeAsFollower(conflicting),
                "the re-delegation and the code reader touch the authority's code, so parallel execution must stop");
        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, chain.executeAsFollower(control),
                "control: independent txs in the same sublist layout must execute");
        assertEquals(ImportResult.INVALID_BLOCK, chain.world.getBlockChain().tryToConnect(conflicting),
                "the block with the conflicting layout must not be imported");
    }

    /**
     * Setup: RSKIP-144 on; authority A delegated to DESTRUCTIBLE_COUNTER (D3).
     * Block (crafted), sublist edges [1, 2]:
     *   sublist 1: sponsor calls D3 with call data, so D3 self-destructs
     *   sublist 2: sponsor2 calls A, which loads D3's code through the delegation
     * Expected: the follower detects the collision on D3 and stops the block.
     * Control: the same layout with an unrelated tx in sublist 2 executes.
     */
    @Test
    void craftedBlock_delegateSelfDestructAndDelegatedCallInDifferentSublists_collisionIsDetected() {
        Chain chain = new Chain(0);
        chain.delegate(chain.destructible);

        Transaction destroy = call(chain.sponsor, chain.nonceOf(chain.sponsor), chain.destructible, new byte[]{0x01});
        Transaction delegatedCall = call(chain.sponsor2, 0, chain.authority.getAddress(), null);
        Transaction independent = call(chain.sponsor4, 0, UNRELATED, null);

        Block conflicting = chain.unexecutedBlockWithSublists(new short[]{1, 2}, destroy, delegatedCall);
        Block control = chain.unexecutedBlockWithSublists(new short[]{1, 2}, destroy, independent);

        assertEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, chain.executeAsFollower(conflicting),
                "the self-destruct and the delegated call both touch the delegate, so parallel execution must stop");
        assertNotEquals(BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT, chain.executeAsFollower(control),
                "control: independent txs in the same sublist layout must execute");
        assertEquals(ImportResult.INVALID_BLOCK, chain.world.getBlockChain().tryToConnect(conflicting),
                "the block with the conflicting layout must not be imported");
    }

    /**
     * Each index in {@code txIndexes} must be in the same parallel sublist as the first one, or in the
     * sequential part after the parallel sublists. Otherwise the parallel path could run them at the same time.
     */
    private static void assertDependentTxsNotInAnotherParallelSublist(short[] edges, int... txIndexes) {
        assertTrue(edges != null && edges.length > 0, "the parallel block must use at least one parallel sublist");
        int first = sublistOf(edges, txIndexes[0]);
        for (int i = 1; i < txIndexes.length; i++) {
            int sublist = sublistOf(edges, txIndexes[i]);
            assertTrue(sublist == first || sublist == -1,
                    "tx " + txIndexes[i] + " depends on tx " + txIndexes[0] + " but is in another parallel sublist; edges "
                            + Arrays.toString(edges));
        }
    }

    /** The parallel sublist that holds the tx, or -1 when the tx is in the sequential part. */
    private static int sublistOf(short[] edges, int txIndex) {
        for (int i = 0; i < edges.length; i++) {
            if (txIndex < edges[i]) {
                return i;
            }
        }
        return -1;
    }

    private static class Chain {
        final World world;
        final Account authority;
        final Account sponsor;
        final Account sponsor2;
        final Account sponsor3;
        final Account sponsor4;
        final RskAddress counter;
        final RskAddress store22;
        final RskAddress destructible;
        final RskAddress probe;

        Chain(int rskip144) {
            TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                    .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                    .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                    .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                    .withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(rskip144)));
            world = new World(config);

            authority = saveAccount("authority", BALANCE, null);
            sponsor = saveAccount("sponsor", BALANCE, null);
            sponsor2 = saveAccount("sponsor2", BALANCE, null);
            sponsor3 = saveAccount("sponsor3", BALANCE, null);
            sponsor4 = saveAccount("sponsor4", BALANCE, null);
            counter = saveAccount("counter", null, COUNTER).getAddress();
            store22 = saveAccount("store22", null, STORE_22).getAddress();
            destructible = saveAccount("destructible", null, DESTRUCTIBLE_COUNTER).getAddress();
            // PUSH20 authority, EXTCODEHASH, PUSH1 0, SSTORE, STOP
            probe = saveAccount("probe", null,
                    ByteUtil.merge(new byte[]{0x73}, authority.getAddress().getBytes(), Hex.decode("3f60005500"))).getAddress();
        }

        private Account saveAccount(String name, Coin balance, byte[] code) {
            AccountBuilder builder = new AccountBuilder(world).name(name);
            if (balance != null) {
                builder.balance(balance);
            }
            if (code != null) {
                builder.code(code);
            }
            Account account = builder.build();
            world.saveAccount(name, account);
            return account;
        }

        RepositorySnapshot state() {
            return Rskip545TestSupport.snapshotAtBest(world);
        }

        long nonceOf(Account account) {
            return state().getNonce(account.getAddress()).longValue();
        }

        DataWord storage(RskAddress address) {
            DataWord value = state().getStorageValue(address, DataWord.ZERO);
            return value == null ? DataWord.ZERO : value;
        }

        /** Delegates the authority to {@code delegate} with a sponsor type-4, in its own block. */
        void delegate(RskAddress delegate) {
            connect(type4(sponsor, nonceOf(sponsor), RECEIVER, tuple(authority, delegate, nonceOf(authority))));
            assertArrayEquals(DelegationCodeResolver.createDelegatedCode(delegate), state().getCode(authority.getAddress()));
        }

        /** Builds a block with exactly these txs (real execution, as an honest miner) and imports it. */
        Block connect(Transaction... txs) {
            Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                    .trieStore(world.getTrieStore())
                    .parent(world.getBlockChain().getBestBlock())
                    .transactions(Arrays.asList(txs))
                    // Large enough that the parallel sublists have room, so the miner can use them.
                    .gasLimit(BigInteger.valueOf(40_000_000))
                    .build(world.getConfig());
            assertEquals(Arrays.asList(txs), block.getTransactionsList(), "execution must keep every transaction");
            assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block), "the node must accept the block");
            return block;
        }

        /** Builds a block with these txs WITHOUT executing them, and sets its RSKIP-144 sublist edges. */
        Block unexecutedBlockWithSublists(short[] edges, Transaction... txs) {
            Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                    .trieStore(world.getTrieStore())
                    .parent(world.getBlockChain().getBestBlock())
                    .transactions(Arrays.asList(txs))
                    // Large enough that each parallel sublist gets a gas limit above the txs' gas.
                    .gasLimit(BigInteger.valueOf(40_000_000))
                    .buildWithoutExecution();
            block.getHeader().setTxExecutionSublistsEdges(edges);
            return block;
        }

        /** Executes the block as validation does (no discarding, no saving). */
        BlockResult executeAsFollower(Block block) {
            return world.getBlockExecutor().execute(null, 0, block, world.getBlockChain().getBestBlock().getHeader(), false, false, false);
        }
    }

    private static SetCodeAuthorization tuple(Account authority, RskAddress delegate, long nonce) {
        return Rskip545TestSupport.createSignedAuthorization(authority.getEcKey(), delegate, BigInteger.valueOf(nonce), CHAIN_ID);
    }

    private static Transaction type4(Account sender, long nonce, RskAddress to, SetCodeAuthorization... tuples) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(BigInteger.valueOf(nonce))
                .maxPriorityFeePerGas(Coin.valueOf(10))
                .maxFeePerGas(Coin.valueOf(10))
                .gasLimit(BigInteger.valueOf(500_000))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(Arrays.asList(tuples))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Transaction call(Account sender, long nonce, RskAddress to, byte[] data) {
        TransactionBuilder builder = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(300_000))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID);
        if (data != null) {
            builder.data(data);
        }
        Transaction tx = builder.build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }
}
