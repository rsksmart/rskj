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

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.ethereum.core.Account;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.Web3Mocks;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.when;

public class TxPoolModuleImplTest {

    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
    private TxPoolModule txPoolModule;
    private TransactionPool transactionPool;
    private Map<Integer, Account> accountMap;

    @Before
    public void setup(){
        transactionPool = Web3Mocks.getMockTransactionPool();
        txPoolModule = new TxPoolModuleImpl(transactionPool);
        accountMap = new HashMap();
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
    public void txpool_content_basic() throws IOException {
        String result = txPoolModule.content();
        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);
        checkFieldIsObject(node,"pending");
        checkFieldIsObject(node,"queued");
    }

    @Test
    public void txpool_inspect_basic() throws IOException {
        String result = txPoolModule.inspect();
        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);
        checkFieldIsObject(node,"pending");
        checkFieldIsObject(node,"queued");
    }

    @Test
    public void txpool_status_basic() throws IOException {
        String result = txPoolModule.status();
        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);
        checkFieldIsNumber(node,"pending");
        checkFieldIsNumber(node,"queued");
    }

    @Test
    public void txpool_content_oneTx() throws Exception {
        Transaction tx = createSampleTransaction();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        String result = txPoolModule.content();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx.getNonceAsInteger().toString());
        nonceNode.elements().forEachRemaining(item -> assertFullTransaction(tx, item));
    }

    @Test
    public void txpool_inspect_oneTx() throws Exception {
        Transaction tx = createSampleTransaction();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        String result = txPoolModule.inspect();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        checkFieldIsEmpty(node, "queued");
        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode senderNode = checkFieldIsObject(pendingNode, tx.getSender().toString());
        JsonNode nonceNode = checkFieldIsArray(senderNode, tx.getNonceAsInteger().toString());
        nonceNode.elements().forEachRemaining(item -> assertSummaryTransaction(tx, item));
    }

    @Test
    public void txpool_content_sameNonce() throws Exception {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        String result = txPoolModule.content();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

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
    public void txpool_inspect_sameNonce() throws Exception {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        String result = txPoolModule.inspect();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

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
    public void txpool_content_sameSender() throws Exception {
        Transaction tx1 = createSampleTransaction(0, 1, 1, 0);
        Transaction tx2 = createSampleTransaction(0, 2, 1, 1);
        Transaction tx3 = createSampleTransaction(0, 3, 1, 2);

        Transaction tx4 = createSampleTransaction(1, 3, 1, 0);
        Transaction tx5 = createSampleTransaction(1, 3, 1, 1);
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3, tx4, tx5);
        List<Transaction> txs = Arrays.asList(tx4, tx5);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        String result = txPoolModule.content();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertFullTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertFullTransaction);
    }

    @Test
    public void txpool_inspect_sameSender() throws Exception {
        Transaction tx1 = createSampleTransaction(0, 1, 1, 0);
        Transaction tx2 = createSampleTransaction(0, 2, 1, 1);
        Transaction tx3 = createSampleTransaction(0, 3, 1, 2);

        Transaction tx4 = createSampleTransaction(1, 3, 1, 0);
        Transaction tx5 = createSampleTransaction(1, 3, 1, 1);
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3, tx4, tx5);
        List<Transaction> txs = Arrays.asList(tx4, tx5);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        String result = txPoolModule.inspect();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertSummaryTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertSummaryTransaction);
    }

    @Test
    public void txpool_content_manyTxs() throws Exception {
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
        String result = txPoolModule.content();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertFullTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertFullTransaction);
    }

    @Test
    public void txpool_inspect_manyTxs() throws Exception {
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
        String result = txPoolModule.inspect();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode pendingNode = checkFieldIsObject(node, "pending");
        JsonNode queuedNode = checkFieldIsObject(node, "queued");

        checkGroupedTransactions(transactions, pendingNode, this::assertSummaryTransaction);
        checkGroupedTransactions(txs, queuedNode, this::assertSummaryTransaction);
    }

    @Test
    public void txpool_status_oneTx() throws Exception {
        Transaction tx = createSampleTransaction();
        when(transactionPool.getPendingTransactions()).thenReturn(Collections.singletonList(tx));
        String result = txPoolModule.status();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode queuedNode = checkFieldIsNumber(node, "queued");
        JsonNode pendingNode = checkFieldIsNumber(node, "pending");
        Assert.assertEquals(0, queuedNode.asInt());
        Assert.assertEquals(1, pendingNode.asInt());
    }

    @Test
    public void txpool_status_manyPending() throws Exception {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        String result = txPoolModule.status();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode queuedNode = checkFieldIsNumber(node, "queued");
        JsonNode pendingNode = checkFieldIsNumber(node, "pending");
        Assert.assertEquals(0, queuedNode.asInt());
        Assert.assertEquals(transactions.size(), pendingNode.asInt());
    }

    @Test
    public void txpool_status_manyTxs() throws Exception {
        Transaction tx1 = createSampleTransaction();
        Transaction tx2 = createSampleTransaction();
        Transaction tx3 = createSampleTransaction();
        Transaction tx4 = createSampleTransaction();
        List<Transaction> transactions = Arrays.asList(tx1, tx2, tx3);
        List<Transaction> txs = Arrays.asList(tx1, tx4);

        when(transactionPool.getPendingTransactions()).thenReturn(transactions);
        when(transactionPool.getQueuedTransactions()).thenReturn(txs);
        String result = txPoolModule.status();

        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.reader().forType(JsonNode.class).readValue(result);

        JsonNode queuedNode = checkFieldIsNumber(node, "queued");
        JsonNode pendingNode = checkFieldIsNumber(node, "pending");
        Assert.assertEquals(txs.size(), queuedNode.asInt());
        Assert.assertEquals(transactions.size(), pendingNode.asInt());
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
        Assert.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assert.assertTrue(fieldNode.isArray());
        return fieldNode;
    }

    private JsonNode checkFieldIsObject(JsonNode node, String fieldName) {
        Assert.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assert.assertTrue(fieldNode.isObject());
        return fieldNode;
    }

    private JsonNode checkFieldIsNumber(JsonNode node, String fieldName) {
        Assert.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assert.assertTrue(fieldNode.isNumber());
        return fieldNode;
    }

    private void checkFieldIsEmpty(JsonNode node, String fieldName) {
        Assert.assertTrue(node.has(fieldName));
        JsonNode fieldNode = node.get(fieldName);
        Assert.assertTrue(fieldNode.isObject());
        Assert.assertFalse(fieldNode.fields().hasNext());
    }

    private void assertFullTransaction(Transaction tx, JsonNode transactionNode) {
        Assert.assertTrue(transactionNode.has("blockhash"));
        Assert.assertEquals(transactionNode.get("blockhash").asText(), "0x0000000000000000000000000000000000000000000000000000000000000000");
        Assert.assertTrue(transactionNode.has("blocknumber"));
        Assert.assertEquals(transactionNode.get("blocknumber"), jsonNodeFactory.nullNode());
        Assert.assertTrue(transactionNode.has("from"));
        Assert.assertEquals(transactionNode.get("from").asText(), tx.getSender().toString());
        Assert.assertTrue(transactionNode.has("gas"));
        Assert.assertEquals(transactionNode.get("gas").asText(), tx.getGasLimitAsInteger().toString());
        Assert.assertTrue(transactionNode.has("gasPrice"));
        Assert.assertEquals(transactionNode.get("gasPrice").asText(), tx.getGasPrice().toString());
        Assert.assertTrue(transactionNode.has("hash"));
        Assert.assertEquals(transactionNode.get("hash").asText(), tx.getHash().toHexString());
        Assert.assertTrue(transactionNode.has("input"));
        Assert.assertEquals(transactionNode.get("input").asText(), Hex.toHexString(tx.getData()));
        Assert.assertTrue(transactionNode.has("nonce"));
        Assert.assertEquals(transactionNode.get("nonce").asText(), tx.getNonceAsInteger().toString());
        Assert.assertTrue(transactionNode.has("to"));
        Assert.assertEquals(transactionNode.get("to").asText(), tx.getReceiveAddress().toString());
        Assert.assertTrue(transactionNode.has("transactionIndex"));
        Assert.assertEquals(transactionNode.get("transactionIndex"), jsonNodeFactory.nullNode());
        Assert.assertTrue(transactionNode.has("value"));
        Assert.assertEquals(transactionNode.get("value").asText(), tx.getValue().toString());
    }

    private void assertSummaryTransaction(Transaction tx, JsonNode summaryNode) {
        String summary = "{}: {} wei + {} x {} gas";
        String summaryFormatted = String.format(summary,
                tx.getReceiveAddress().toString(),
                tx.getValue().toString(),
                tx.getGasLimitAsInteger().toString(),
                tx.getGasPrice().toString());

        Assert.assertEquals(summaryFormatted, summaryNode.asText());
    }
}