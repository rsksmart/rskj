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
    private final Account accountA = new AccountBuilder().name("accountA").build();
    private final Account accountB = new AccountBuilder().name("accountB").build();
    private final Account receiver = new AccountBuilder().name("receiver").build();
    private final BlockGenerator blockGenerator = new BlockGenerator();

    private RskTestContext context;
    private Blockchain blockchain;
    private TransactionPoolImpl pool;
    private Block genesis;

    @BeforeEach
    void setUp() throws IOException {
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
        Assertions.assertTrue(isDelegatedAt(genesis, delegatedAccount));

        Transaction stale = tx(delegatedAccount, 0, 1000);
        Assertions.assertTrue(pool.addTransaction(stale).pendingTransactionsWereAdded());

        // chain X: a different transaction from the delegated account consumes nonce 0
        Transaction minedOnX = tx(delegatedAccount, 0, 2000);
        Block x1 = connect(genesis, minedOnX);
        Assertions.assertEquals(BigInteger.ONE, stateNonce(x1, delegatedAccount));

        Transaction next = tx(delegatedAccount, 1, 1000);
        Assertions.assertTrue(pool.addTransaction(next).pendingTransactionsWereAdded());

        // heavier chain Y from genesis without any transaction of the delegated account
        Block y1 = connectSide(genesis);
        Block y2 = connect(y1);
        Assertions.assertEquals(y2.getHash(), blockchain.getBestBlock().getHash());
        Assertions.assertEquals(BigInteger.ZERO, stateNonce(y2, delegatedAccount));

        // the nonce-1 transaction is gapped again and gives way to the retracted nonce-0 one
        List<Keccak256> pooledForAccount = poolTransactions().stream()
                .filter(t -> t.getSender().equals(delegatedAccount.getAddress()))
                .map(Transaction::getHash)
                .collect(Collectors.toList());
        Assertions.assertEquals(List.of(minedOnX.getHash()), pooledForAccount);
    }

    @Test
    void delegatedAccountKeepsExecutableTransactionOnNewBestBlock() {
        Transaction executable = tx(delegatedAccount, 0, 1000);
        Assertions.assertTrue(pool.addTransaction(executable).pendingTransactionsWereAdded());

        connect(genesis);

        Assertions.assertEquals(Set.of(executable.getHash()), poolHashes());
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

        Block mined = connect(delegating, pool.getPendingTransactions().toArray(new Transaction[0]));
        Assertions.assertEquals(BigInteger.TWO, stateNonce(mined, accountA));
        Assertions.assertTrue(poolTransactions().isEmpty());
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
        return DelegationCodeResolver.isDelegatedCode(
                context.getRepositoryLocator().snapshotAt(block.getHeader()).getCode(account.getAddress()));
    }

    private Block connectSide(Block parent, Transaction... txs) {
        Block block = blockGenerator.createChildBlock(parent, Arrays.asList(txs));
        context.getBlockExecutor().executeAndFillAll(block, parent.getHeader());
        block.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockchain.tryToConnect(block));
        return block;
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
        Block block = blockGenerator.createChildBlock(parent, Arrays.asList(txs));
        context.getBlockExecutor().executeAndFillAll(block, parent.getHeader());
        block.seal();
        Assertions.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block));
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
            for (Account account : List.of(accountA, accountB)) {
                alloc.putObject(account.getAddress().toString()).put("balance", BALANCE_IN_WEI);
            }
            ObjectNode delegated = alloc.putObject(delegatedAccount.getAddress().toString());
            delegated.put("balance", BALANCE_IN_WEI);
            ObjectNode contract = delegated.putObject("contract");
            contract.put("code", Hex.toHexString(DelegationCodeResolver.createDelegatedCode(DELEGATE)));
            contract.putObject("data");
            return new ByteArrayInputStream(mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
