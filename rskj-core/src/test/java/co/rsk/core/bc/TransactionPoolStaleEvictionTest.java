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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exercises {@link TransactionPoolImpl#processBest(Block)} on a real chain: blocks are executed
 * and stored, so the nonce seen by the pool moves because a block was executed.
 */
class TransactionPoolStaleEvictionTest {
    private static final String BALANCE_IN_WEI = "1000000000000000000";
    private static final RskAddress DELEGATE = new RskAddress("00000000000000000000000000000000000000d1");

    @TempDir
    public Path tempDir;

    private final Account delegatedAccount = new AccountBuilder().name("delegatedAccount").build();
    private final Account codeOnlyAccount = new AccountBuilder().name("codeOnlyAccount").build();
    private final Account accountA = new AccountBuilder().name("accountA").build();
    private final Account accountB = new AccountBuilder().name("accountB").build();
    private final Account receiver = new AccountBuilder().name("receiver").build();
    private final BlockGenerator blockGenerator = new BlockGenerator();

    private RskTestContext context;
    private Blockchain blockchain;
    private TransactionPoolImpl pool;
    private Block genesis;

    @BeforeEach
    void setUp() {
        context = new RskTestContext(tempDir, "--regtest") {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), genesisJson(), BigInteger.ZERO, true, true, true);
            }

            @Override
            protected BlockValidator buildBlockValidator() {
                // header and proof of work rules are out of scope; blocks are still executed and their state root checked
                return new DummyBlockValidator();
            }
        };
        blockchain = context.getBlockchain();
        pool = (TransactionPoolImpl) context.getTransactionPool();
        genesis = blockchain.getBestBlock();
        // not calling start() to avoid creating cleaner threads
        pool.processBest(genesis);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void evictsStalePendingTransactionOnNewBestBlock() {
        Transaction stale = tx(accountA, 0, 1000);
        Transaction nextNonce = tx(accountA, 1, 1000);
        Transaction otherSender = tx(accountB, 0, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(nextNonce).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(otherSender).pendingTransactionsWereAdded());

        // a different transaction from the same sender consumes nonce 0
        connect(genesis, tx(accountA, 0, 2000));

        Assertions.assertEquals(Set.of(nextNonce.getHash(), otherSender.getHash()), poolHashes());
    }

    @Test
    void evictsStaleQueuedTransactionOnNewBestBlock() {
        Transaction stale = tx(accountA, 1, 1000);
        Transaction stillGapped = tx(accountA, 3, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).queuedTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(stillGapped).queuedTransactionsWereAdded());

        // different transactions from the same sender consume nonces 0 and 1
        connect(genesis, tx(accountA, 0, 2000), tx(accountA, 1, 2000));

        Assertions.assertEquals(Set.of(stillGapped.getHash()), poolHashes());
        Assertions.assertEquals(List.of(stillGapped), pool.getQueuedTransactions());
    }

    @Test
    void delegatedAccountKeepsOnlyRetractedTransactionAfterReorg() {
        Assertions.assertTrue(context.getRskSystemProperties().getActivationConfig().isActive(ConsensusRule.RSKIP545, 1));
        Block delegating = delegate(genesis);

        Transaction stale = tx(delegatedAccount, 1, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).pendingTransactionsWereAdded());

        // chain X: a different transaction from the delegated account consumes nonce 1
        Transaction minedOnX = tx(delegatedAccount, 1, 2000);
        Block x2 = connect(delegating, minedOnX);
        Assertions.assertEquals(BigInteger.TWO, stateNonce(x2, delegatedAccount));

        Transaction next = tx(delegatedAccount, 2, 1000);
        Assertions.assertTrue(pool.addTransaction(next).pendingTransactionsWereAdded());

        // heavier chain Y from the delegating block without any transaction of the delegated account
        Block y2 = connectSide(delegating);
        Block y3 = connect(y2);
        Assertions.assertEquals(y3.getHash(), blockchain.getBestBlock().getHash());
        Assertions.assertEquals(BigInteger.ONE, stateNonce(y3, delegatedAccount));

        // the nonce-2 transaction is gapped again and gives way to the retracted nonce-1 one
        List<Keccak256> pooledForAccount = poolTransactions().stream()
                .filter(t -> t.getSender().equals(delegatedAccount.getAddress()))
                .map(Transaction::getHash)
                .collect(Collectors.toList());
        Assertions.assertEquals(List.of(minedOnX.getHash()), pooledForAccount);
    }

    @Test
    void delegatedAccountKeepsExecutableTransactionOnNewBestBlock() {
        Block delegating = delegate(genesis);
        Transaction executable = tx(delegatedAccount, 1, 1000);
        Assertions.assertTrue(pool.addTransaction(executable).pendingTransactionsWereAdded());

        Block block = connect(delegating);

        Assertions.assertEquals(Set.of(executable.getHash()), poolHashes());
        assertPoolKeepsWhatAdmissionAccepts(block, delegatedAccount, executable);
    }

    @Test
    void delegatedAccountExecutableTransactionKeepsItsAdmissionBlock() {
        Block delegating = delegate(genesis);
        Transaction executable = tx(delegatedAccount, 1, 1000);
        Assertions.assertTrue(pool.addTransaction(executable).pendingTransactionsWereAdded());

        Block block = connect(delegating);

        // admitted at block 1, so with a depth of 10 it is outdated at block 12
        pool.removeObsoleteTransactions(block.getNumber() + 10, 10, 0);
        Assertions.assertTrue(poolTransactions().isEmpty());
    }

    @Test
    void accountDelegatedByAnotherSenderKeepsOnlyExecutableTransaction() {
        Transaction stale = tx(accountA, 0, 1000);
        Transaction executable = tx(accountA, 1, 1000);
        Transaction gapped = tx(accountA, 2, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(executable).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(gapped).pendingTransactionsWereAdded());

        // accountB's set code transaction delegates accountA and consumes its nonce 0
        Block block = connect(genesis, setCode(accountB, accountA, 0));
        Assertions.assertEquals(BigInteger.ONE, stateNonce(block, accountA));
        Assertions.assertTrue(isDelegatedAt(block, accountA));

        Assertions.assertEquals(Set.of(executable.getHash()), poolHashes());
        assertPoolKeepsWhatAdmissionAccepts(block, accountA, stale, executable, gapped);
    }

    @Test
    void accountWithDelegationCodeButNoAuthorityFlagIsTreatedAsPlainAccount() {
        Assertions.assertTrue(DelegationCodeResolver.isDelegatedCode(codeAt(genesis, codeOnlyAccount)));
        Assertions.assertFalse(isDelegatedAt(genesis, codeOnlyAccount));

        Transaction first = tx(codeOnlyAccount, 0, 1000);
        Transaction second = tx(codeOnlyAccount, 1, 1000);
        Transaction gapped = tx(codeOnlyAccount, 3, 1000);
        Assertions.assertTrue(pool.addTransaction(first).pendingTransactionsWereAdded());
        // a delegated account is refused a second transaction
        TransactionPoolAddResult result = pool.addTransaction(second);
        Assertions.assertTrue(result.pendingTransactionsWereAdded(), result.getErrorMessage());
        Assertions.assertTrue(pool.addTransaction(gapped).queuedTransactionsWereAdded());

        // a delegated account's gapped transaction is evicted on a new best block
        connect(genesis);

        Assertions.assertEquals(Set.of(first.getHash(), second.getHash(), gapped.getHash()), poolHashes());
    }

    @Test
    void transactionsEvictedOnLosingTipAreNotRestoredByReorg() {
        Transaction stale = tx(accountA, 0, 1000);
        Transaction executable = tx(accountA, 1, 1000);
        Transaction gapped = tx(accountA, 2, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(executable).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(gapped).pendingTransactionsWereAdded());

        // losing chain X: accountB's set code transaction delegates accountA and consumes its nonce 0
        Transaction delegation = setCode(accountB, accountA, 0);
        connect(genesis, delegation);
        Assertions.assertEquals(Set.of(executable.getHash()), poolHashes());

        // heavier chain Y without the delegation: accountA is a plain account at nonce 0 again
        Block y1 = connectSide(genesis);
        Block y2 = connect(y1);
        Assertions.assertFalse(isDelegatedAt(y2, accountA));
        Assertions.assertEquals(BigInteger.ZERO, stateNonce(y2, accountA));

        // nonces 0 and 2 are not restored; only the retracted delegation comes back
        Assertions.assertEquals(Set.of(executable.getHash(), delegation.getHash()), poolHashes());

        // once the delegation is mined again, nonce 1 is executable, as it was on chain X
        Block y3 = connect(y2, delegation);
        Assertions.assertEquals(BigInteger.ONE, stateNonce(y3, accountA));
        Assertions.assertEquals(Set.of(executable.getHash()), poolHashes());

        Block y4 = connect(y3, executable);
        Assertions.assertEquals(BigInteger.TWO, stateNonce(y4, accountA));
        Assertions.assertTrue(poolTransactions().isEmpty());
    }

    @Test
    void accountDelegatedWithQueuedTransactionAcceptsAndMinesNextNonce() {
        Transaction stale = tx(accountA, 0, 1000);
        Transaction gapped = tx(accountA, 2, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).pendingTransactionsWereAdded());
        Assertions.assertTrue(pool.addTransaction(gapped).queuedTransactionsWereAdded());

        // accountB's set code transaction delegates accountA and consumes its nonce 0
        Block delegating = connect(genesis, setCode(accountB, accountA, 0));
        Assertions.assertEquals(BigInteger.ONE, stateNonce(delegating, accountA));
        Assertions.assertTrue(isDelegatedAt(delegating, accountA));
        assertPoolKeepsWhatAdmissionAccepts(delegating, accountA, stale, gapped);

        Transaction next = tx(accountA, 1, 1000);
        TransactionPoolAddResult result = pool.addTransaction(next);
        Assertions.assertTrue(result.pendingTransactionsWereAdded(), result.getErrorMessage());
        Assertions.assertEquals(List.of(next), pool.getPendingTransactions());

        Block mined = connect(delegating, pool.getPendingTransactions().toArray(new Transaction[0]));
        Assertions.assertEquals(BigInteger.TWO, stateNonce(mined, accountA));
        Assertions.assertTrue(poolTransactions().isEmpty());
    }

    @Test
    void accountDelegatedUpToQueuedNonceGetsQueuedTransactionMined() {
        Transaction queued = tx(accountA, 1, 1000);
        Assertions.assertTrue(pool.addTransaction(queued).queuedTransactionsWereAdded());

        // accountB's set code transaction delegates accountA and consumes its nonce 0
        Block delegating = connect(genesis, setCode(accountB, accountA, 0));
        Assertions.assertEquals(BigInteger.ONE, stateNonce(delegating, accountA));
        Assertions.assertTrue(isDelegatedAt(delegating, accountA));

        Assertions.assertEquals(List.of(queued), pool.getPendingTransactions());
        Assertions.assertTrue(pool.getQueuedTransactions().isEmpty());
        assertPoolKeepsWhatAdmissionAccepts(delegating, accountA, queued);

        Block mined = connect(delegating, pool.getPendingTransactions().toArray(new Transaction[0]));
        Assertions.assertEquals(BigInteger.TWO, stateNonce(mined, accountA));
        Assertions.assertTrue(poolTransactions().isEmpty());
    }

    @Test
    void plainAccountQueuedTransactionAtStateNonceStaysQueued() {
        Transaction queued = tx(accountA, 1, 1000);
        Assertions.assertTrue(pool.addTransaction(queued).queuedTransactionsWereAdded());

        // a different transaction from the same sender consumes nonce 0, and accountA stays a plain account
        Block block = connect(genesis, tx(accountA, 0, 2000));
        Assertions.assertEquals(BigInteger.ONE, stateNonce(block, accountA));
        Assertions.assertFalse(isDelegatedAt(block, accountA));

        // only a delegated account's queued transaction is promoted
        Assertions.assertEquals(List.of(queued), pool.getQueuedTransactions());
        Assertions.assertTrue(pool.getPendingTransactions().isEmpty());
    }

    @Test
    void queuedTransactionRefusedByAdmissionOnPromotionIsDropped() {
        // admission checks a queued transaction's gas cost against the balance, but not its value
        Transaction unaffordable = tx(accountA, 1, 2_000_000_000_000_000_000L);
        Assertions.assertTrue(pool.addTransaction(unaffordable).queuedTransactionsWereAdded());

        // accountB's set code transaction delegates accountA and consumes its nonce 0
        Block delegating = connect(genesis, setCode(accountB, accountA, 0));
        Assertions.assertEquals(BigInteger.ONE, stateNonce(delegating, accountA));
        Assertions.assertTrue(isDelegatedAt(delegating, accountA));

        Assertions.assertTrue(poolTransactions().isEmpty());
        assertPoolKeepsWhatAdmissionAccepts(delegating, accountA, unaffordable);
    }

    /**
     * Delegates {@code delegatedAccount} through accountB's set code transaction, which also
     * consumes its nonce 0, so the account carries both the delegation code and the flag.
     */
    private Block delegate(Block parent) {
        Block block = connect(parent, setCode(accountB, delegatedAccount, 0));
        Assertions.assertTrue(isDelegatedAt(block, delegatedAccount));
        Assertions.assertEquals(BigInteger.ONE, stateNonce(block, delegatedAccount));
        return block;
    }

    /**
     * Asserts that the pool holds exactly those of the account's candidate transactions that
     * admission accepts against the state at {@code best}. A new pool at that block is offered
     * the candidates in nonce order, so a change to the admission rules that the reconciliation
     * on a new best block does not follow, or the reverse, fails here.
     */
    private void assertPoolKeepsWhatAdmissionAccepts(Block best, Account account, Transaction... candidates) {
        TransactionPoolImpl admissionPool = new TransactionPoolImpl(
                context.getRskSystemProperties(),
                context.getRepositoryLocator(),
                context.getBlockStore(),
                context.getBlockFactory(),
                null,
                context.getTransactionExecutorFactory(),
                context.getReceivedTxSignatureCache(),
                context.getRskSystemProperties().txOutdatedThreshold(),
                context.getRskSystemProperties().txOutdatedTimeout(),
                new TxQuotaChecker(System::currentTimeMillis, context.getReceivedTxSignatureCache()),
                context.getGasPriceTracker());
        admissionPool.processBest(best);

        List<Transaction> inNonceOrder = Arrays.stream(candidates)
                .sorted(Comparator.comparing(Transaction::getNonceAsInteger))
                .collect(Collectors.toList());
        for (Transaction candidate : inNonceOrder) {
            admissionPool.addTransaction(candidate);
        }

        Assertions.assertEquals(hashesOf(admissionPool, account), hashesOf(pool, account));
    }

    private Set<Keccak256> hashesOf(TransactionPoolImpl transactionPool, Account account) {
        return Stream.concat(transactionPool.getPendingTransactions().stream(), transactionPool.getQueuedTransactions().stream())
                .filter(t -> t.getSender().equals(account.getAddress()))
                .map(Transaction::getHash)
                .collect(Collectors.toSet());
    }

    private byte[] codeAt(Block block, Account account) {
        return context.getRepositoryLocator().snapshotAt(block.getHeader()).getCode(account.getAddress());
    }

    private Transaction setCode(Account sender, Account authority, long authorityNonce) {
        return new TransactionBuilder()
                .transactionType(TransactionType.TYPE_4.getByteCode())
                .sender(sender)
                .receiver(receiver)
                .gasLimit(BigInteger.valueOf(100_000))
                .authorization(authority, DELEGATE, BigInteger.valueOf(authorityNonce), Constants.REGTEST_CHAIN_ID)
                .build();
    }

    private BigInteger stateNonce(Block block, Account account) {
        return context.getRepositoryLocator().snapshotAt(block.getHeader()).getNonce(account.getAddress());
    }

    private boolean isDelegatedAt(Block block, Account account) {
        return context.getRepositoryLocator().snapshotAt(block.getHeader()).isActiveDelegatedEOA(account.getAddress());
    }

    private Transaction tx(Account sender, long nonce, long valueInWei) {
        return new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(nonce)
                .value(BigInteger.valueOf(valueInWei))
                .gasPrice(BigInteger.ONE)
                .build();
    }

    private Block connect(Block parent, Transaction... txs) {
        Block block = executedChild(parent, txs);
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block));
        return block;
    }

    private Block connectSide(Block parent) {
        Block block = executedChild(parent);
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block));
        return block;
    }

    private Block executedChild(Block parent, Transaction... txs) {
        Block block = blockGenerator.createChildBlock(parent, Arrays.asList(txs));
        context.getBlockExecutor().executeAndFillAll(block, parent.getHeader());
        block.seal();
        return block;
    }

    private List<Transaction> poolTransactions() {
        return Stream.concat(pool.getPendingTransactions().stream(), pool.getQueuedTransactions().stream())
                .collect(Collectors.toList());
    }

    private Set<Keccak256> poolHashes() {
        return poolTransactions().stream()
                .map(Transaction::getHash)
                .collect(Collectors.toSet());
    }

    private InputStream genesisJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(
                    getClass().getResourceAsStream("/genesis/rsk-unittests.json"));
            ObjectNode alloc = (ObjectNode) root.get("alloc");
            for (Account account : List.of(accountA, accountB, delegatedAccount)) {
                alloc.putObject(account.getAddress().toString()).put("balance", BALANCE_IN_WEI);
            }
            // delegation code without the delegation authority flag, which a genesis entry cannot set
            ObjectNode codeOnly = alloc.putObject(codeOnlyAccount.getAddress().toString());
            codeOnly.put("balance", BALANCE_IN_WEI);
            ObjectNode contract = codeOnly.putObject("contract");
            contract.put("code", Hex.toHexString(DelegationCodeResolver.createDelegatedCode(DELEGATE)));
            contract.putObject("data");
            return new ByteArrayInputStream(mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
