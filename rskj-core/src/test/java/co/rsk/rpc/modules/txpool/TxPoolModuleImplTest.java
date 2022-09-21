/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rpc.modules.txpool;

import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.ethereum.core.*;
import org.ethereum.rpc.Web3Mocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.HexUtils;

class TxPoolModuleImplTest {

    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
    private TxPoolModule txPoolModule;
    private TransactionPool transactionPool;
    private Map<Integer, Account> accountMap;
    private SignatureCache signatureCache;

    @BeforeEach
    void setup() {
        signatureCache = new ReceivedTxSignatureCache();
        transactionPool = Web3Mocks.getMockTransactionPool();
        txPoolModule = new TxPoolModuleImpl(transactionPool, signatureCache);
        accountMap = new HashMap<>();
    }

    private Transaction createSampleTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .build();

        return tx;
    }

    private Transaction createSampleTransactionWithoutReceiver() {
        Account sender = new AccountBuilder().name("sender").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .value(BigInteger.TEN)
                .build();

        return tx;
    }

    private Transaction createSampleTransaction(int from, int to, long value, int nonce) {
        Account sender = getAccount(from);
        Account receiver = getAccount(to);

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(nonce)
                .value(BigInteger.valueOf(value))
                .build();

        return tx;
    }

    private Account getAccount(int naccount) {
        Account account = accountMap.get(naccount);
        if (account != null){
            return account;
        }
        account = createAccount(naccount);
        accountMap.put(naccount, account);
        return account;
    }

    private Account createAccount(int naccount) {
        return new AccountBuilder().name("account" + naccount).build();
    }

    @Test
    void txpool_content_basic() {
        JsonNode node = txPoolModule.content();
        checkFieldIsObject(node,"pending");
        checkFieldIsObject(node,"queued");
    }

    @Test
    void txpool_inspect_basic() {
        JsonNode node = txPoolModule.inspect();
        checkFieldIsObject(node,"pending");
        checkFieldIsObject(node,"queued");
    }

    @Test
    void txpool_status_basic() {
        JsonNode node = txPoolModule.status();
        checkFieldIsNumber(node,"pending");
        checkFieldIsNumber(node,"queued");
    }

    @Test
    void txpool_content_oneTx() {
        Transaction tx = createSampleTransaction();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        JsonNode node = txPoolModule.content();

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx.getNonceAsInteger().toString());
        nonceNode.elements().forEachRemaining(item -> assertFullTransaction(tx, item));
    }

    @Test
    void txpool_content_oneTx_no_receiver() {
        Transaction tx = createSampleTransactionWithoutReceiver();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        JsonNode node = txPoolModule.content();

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx.getNonceAsInteger().toString());
        nonceNode.elements().forEachRemaining(item -> assertFullTransaction(tx, item));
    }

    @Test
    void txpool_inspect_oneTx() {
        Transaction tx = createSampleTransaction();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        JsonNode node = txPoolModule.inspect();

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx.getNonceAsInteger().toString());
        nonceNode.elements().forEachRemaining(item -> assertSummaryTransaction(tx, item));
    }

    @Test
    void txpool_content_sameNonce() {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        JsonNode node = txPoolModule.content();

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx1.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx1.getNonceAsInteger().toString());

        int i = 0;
        for (Iterator<JsonNode> iter = nonceNode.elements(); iter.hasNext();){
            JsonNode item = iter.next();
            assertFullTransaction(transactions.get(i), item);
            i++;
        }
    }

    @Test
    void txpool_inspect_sameNonce() {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        JsonNode node = txPoolModule.inspect();

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx1.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx1.getNonceAsInteger().toString());

        int i = 0;
        for (Iterator<JsonNode> iter = nonceNode.elements(); iter.hasNext();){
            JsonNode item = iter.next();
            assertSummaryTransaction(transactions.get(i), item);
            i++;
        }
    }

    @Test
    void txpool_content_sameSender() {
        Transaction tx1 = createSampleTransaction(0, 1, 1, 0);
        Transaction tx2 = createSampleTransaction(0, 2, 1, 1);
        Transaction tx3 = createSampleTransaction(0, 3, 1, 2);

        Transaction tx4 = createSampleTransaction(1, 3, 1, 0);
        Transaction tx5 = createSampleTransaction(1, 3, 1, 1);
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3, tx4, tx5);
        List<Transaction> txs = Arrays.asList(tx4, tx5);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        JsonNode node = txPoolModule.content();

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertFullTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertFullTransaction);
    }

    @Test
    void txpool_inspect_sameSender() {
        Transaction tx1 = createSampleTransaction(0, 1, 1, 0);
        Transaction tx2 = createSampleTransaction(0, 2, 1, 1);
        Transaction tx3 = createSampleTransaction(0, 3, 1, 2);

        Transaction tx4 = createSampleTransaction(1, 3, 1, 0);
        Transaction tx5 = createSampleTransaction(1, 3, 1, 1);
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3, tx4, tx5);
        List<Transaction> txs = Arrays.asList(tx4, tx5);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        JsonNode node = txPoolModule.inspect();

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertSummaryTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertSummaryTransaction);
    }

    @Test
    void txpool_content_manyTxs() {
        Transaction tx1 = createSampleTransaction(0, 1, 1, 0);
        Transaction tx2 = createSampleTransaction(0, 2, 1, 0);
        Transaction tx3 = createSampleTransaction(0, 3, 1, 0);
        Transaction tx4 = createSampleTransaction(1, 3, 1, 0);
        Transaction tx5 = createSampleTransaction(1, 3, 1, 1);
        Transaction tx6 = createSampleTransaction(1, 3, 1, 2);
        Transaction tx7 = createSampleTransaction(2, 3, 1, 0);
        Transaction tx8 = createSampleTransaction(2, 3, 1, 1);

        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3, tx4, tx5);
        List<Transaction> txs = Arrays.asList(tx7, tx8, tx6);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        JsonNode node = txPoolModule.content();

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertFullTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertFullTransaction);
    }

    @Test
    void txpool_inspect_manyTxs() {
        Transaction tx1 = createSampleTransaction(0, 1, 1, 0);
        Transaction tx2 = createSampleTransaction(0, 2, 1, 0);
        Transaction tx3 = createSampleTransaction(0, 3, 1, 0);
        Transaction tx4 = createSampleTransaction(1, 3, 1, 0);
        Transaction tx5 = createSampleTransaction(1, 3, 1, 1);
        Transaction tx6 = createSampleTransaction(1, 3, 1, 2);
        Transaction tx7 = createSampleTransaction(2, 3, 1, 0);
        Transaction tx8 = createSampleTransaction(2, 3, 1, 1);

        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3, tx4, tx5);
        List<Transaction> txs = Arrays.asList(tx7, tx8, tx6);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        JsonNode node = txPoolModule.inspect();

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertSummaryTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertSummaryTransaction);
    }

    @Test
    void txpool_status_oneTx() {
        Transaction tx = createSampleTransaction();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        JsonNode node = txPoolModule.status();

        JsonNode queuedNode = checkFieldIsNumber(node, "queued");
        JsonNode pendingNode = checkFieldIsNumber(node, "pending");
        Assertions.assertEquals(0, queuedNode.asInt());
        Assertions.assertEquals(1, pendingNode.asInt());
    }

    @Test
    void txpool_status_manyPending() {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        JsonNode node = txPoolModule.status();

        JsonNode queuedNode = checkFieldIsNumber(node, "queued");
        JsonNode pendingNode = checkFieldIsNumber(node, "pending");
        Assertions.assertEquals(0, queuedNode.asInt());
        Assertions.assertEquals(transactions.size(), pendingNode.asInt());
    }

    @Test
    void txpool_status_manyTxs() {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        Transaction tx4 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        List<Transaction> txs = Arrays.asList(tx1, tx4);

        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        JsonNode node = txPoolModule.status();

        JsonNode queuedNode = checkFieldIsNumber(node, "queued");
        JsonNode pendingNode = checkFieldIsNumber(node, "pending");
        Assertions.assertEquals(txs.size(), queuedNode.asInt());
        Assertions.assertEquals(transactions.size(), pendingNode.asInt());
    }

    private interface JsonNodeVerifier {
        void verify(Transaction tx, JsonNode node);
    }

    private void checkGroupedTransactions(List<Transaction> transactions, JsonNode txsNode, JsonNodeVerifier verifier) {
        int i = 0;
        for (Iterator<JsonNode> iterSenders = txsNode.elements(); iterSenders.hasNext();){
            JsonNode fieldSender = iterSenders.next();
            for (Iterator<JsonNode> iterNonces = fieldSender.elements(); iterNonces.hasNext();){
                JsonNode fieldNonce = iterNonces.next();
                for (Iterator<JsonNode> iterTxs = fieldNonce.elements(); iterTxs.hasNext();) {
                    JsonNode txNode = iterTxs.next();
                    Transaction tx = transactions.get(i);
                    verifier.verify(tx, txNode);
                    i++;
                }
            }

        }
    }

    private JsonNode checkFieldIsArray(JsonNode node, String fieldName) {
        Assertions.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assertions.assertTrue(fieldNode.isArray());
        return fieldNode;
    }

    private JsonNode checkFieldIsObject(JsonNode node, String fieldName) {
        Assertions.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assertions.assertTrue(fieldNode.isObject());
        return fieldNode;
    }

    private JsonNode checkFieldIsNumber(JsonNode node, String fieldName) {
        Assertions.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assertions.assertTrue(fieldNode.isNumber());
        return fieldNode;
    }

    private void checkFieldIsEmpty(JsonNode node, String fieldName) {
        Assertions.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assertions.assertTrue(fieldNode.isObject());
        Assertions.assertFalse(fieldNode.fields().hasNext());
    }

    private void assertFullTransaction(Transaction tx, JsonNode transactionNode) {
        Assertions.assertTrue(transactionNode.has("blockHash"));
        Assertions.assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", transactionNode.get("blockHash").asText());
        Assertions.assertTrue(transactionNode.has("blockNumber"));
        Assertions.assertEquals(transactionNode.get("blockNumber"), jsonNodeFactory.nullNode());
        Assertions.assertTrue(transactionNode.has("from"));
        Assertions.assertEquals(transactionNode.get("from").asText(), HexUtils.toJsonHex(tx.getSender().getBytes()));
        Assertions.assertTrue(transactionNode.has("gas"));
        Assertions.assertEquals(transactionNode.get("gas").asText(), HexUtils.toQuantityJsonHex(tx.getGasLimitAsInteger()));
        Assertions.assertTrue(transactionNode.has("gasPrice"));
        Assertions.assertEquals(transactionNode.get("gasPrice").asText(), HexUtils.toJsonHex(tx.getGasPrice().getBytes()));
        Assertions.assertTrue(transactionNode.has("hash"));
        Assertions.assertEquals(transactionNode.get("hash").asText(), HexUtils.toJsonHex(tx.getHash().toHexString()));
        Assertions.assertTrue(transactionNode.has("input"));
        Assertions.assertEquals(transactionNode.get("input").asText(), HexUtils.toUnformattedJsonHex(tx.getData()));
        Assertions.assertTrue(transactionNode.has("nonce"));
        Assertions.assertEquals(transactionNode.get("nonce").asText(), HexUtils.toQuantityJsonHex(tx.getNonceAsInteger()));
        Assertions.assertTrue(transactionNode.has("to"));
        Assertions.assertEquals(transactionNode.get("to").asText(), HexUtils.toJsonHex(tx.getReceiveAddress().getBytes()));
        Assertions.assertTrue(transactionNode.has("transactionIndex"));
        Assertions.assertEquals(transactionNode.get("transactionIndex"), jsonNodeFactory.nullNode());
        Assertions.assertTrue(transactionNode.has("value"));
        Assertions.assertEquals(transactionNode.get("value").asText(), HexUtils.toJsonHex(tx.getValue().getBytes()));

        if (tx.getData() != null && tx.getData().length > 0) {
            Assertions.assertTrue(transactionNode.has("data"));
            Assertions.assertEquals(transactionNode.get("data").asText(), HexUtils.toUnformattedJsonHex(tx.getData()));
        }
        else {
            Assertions.assertFalse(transactionNode.has("data"));
        }
    }

    private void assertSummaryTransaction(Transaction tx, JsonNode summaryNode) {
        Assertions.assertEquals(tx.getReceiveAddress().toString() + ": " + tx.getValue().toString() + " wei + " + tx.getGasLimitAsInteger().toString() + " x " + tx.getGasPrice().toString() + " gas", summaryNode.asText());
    }
}