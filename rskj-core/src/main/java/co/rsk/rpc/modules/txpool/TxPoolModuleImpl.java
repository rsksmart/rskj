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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;

public class TxPoolModuleImpl implements TxPoolModule {

    public static final String PENDING = "pending";
    public static final String QUEUED = "queued";
    private final JsonNodeFactory jsonNodeFactory;
    private final TransactionPool transactionPool;

    public TxPoolModuleImpl(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        jsonNodeFactory = JsonNodeFactory.instance;
    }

    /**
     * This method should return 2 dictionaries containing pending and queued transactions
     * Each entry is an origin-address to a batch of scheduled transactions
     * These batches themselves are maps associating nonces with actual transactions.
     * When there are no transactions the answer would be
     * "{"pending": {}, "queued": {}}"
     */
    @Override
    public JsonNode content() {
        Map<String, JsonNode> contentProps = new HashMap<>();
        Map<RskAddress, Map<BigInteger, List<Transaction>>> pendingGrouped = groupTransactions(transactionPool.getPendingTransactions());
        Map<RskAddress, Map<BigInteger, List<Transaction>>> queuedGrouped = groupTransactions(transactionPool.getQueuedTransactions());
        contentProps.put(PENDING, serializeTransactions(pendingGrouped, this::fullSerializer));
        contentProps.put(QUEUED, serializeTransactions(queuedGrouped, this::fullSerializer));
        JsonNode node = jsonNodeFactory.objectNode().setAll(contentProps);
        return node;
    }

    private JsonNode serializeTransactions(
            Map<RskAddress, Map<BigInteger, List<Transaction>>> groupedTransactions,
            Function<Transaction, JsonNode> txSerializer) {
        Map<String, JsonNode> senderProps = new HashMap<>();
        for (Map.Entry<RskAddress, Map<BigInteger, List<Transaction>>> entrySender : groupedTransactions.entrySet()){
            Map<String, JsonNode> nonceProps = new HashMap<>();
            for (Map.Entry<BigInteger, List<Transaction>> entryNonce : entrySender.getValue().entrySet()){
                ArrayNode txsNodes = jsonNodeFactory.arrayNode();
                for (Transaction tx : entryNonce.getValue()) {
                    txsNodes.add(txSerializer.apply(tx));
                }
                nonceProps.put(entryNonce.getKey().toString(),txsNodes);
            }
            senderProps.put(entrySender.getKey().toString(), jsonNodeFactory.objectNode().setAll(nonceProps));
        }
        return jsonNodeFactory.objectNode().setAll(senderProps);
    }

    private JsonNode fullSerializer(Transaction tx) {
        ObjectNode txNode = jsonNodeFactory.objectNode();

        txNode.put("blockHash", "0x0000000000000000000000000000000000000000000000000000000000000000");
        txNode.putNull("blockNumber");
        txNode.putNull("transactionIndex");

        txNode.put("from", HexUtils.toJsonHex(tx.getSender().getBytes()));
        txNode.put("gas", HexUtils.toQuantityJsonHex(tx.getGasLimitAsInteger()));
        txNode.put("gasPrice", HexUtils.toJsonHex(tx.getGasPrice().getBytes()));
        txNode.put("hash", HexUtils.toJsonHex(tx.getHash().toHexString()));
        txNode.put("input", HexUtils.toUnformattedJsonHex(tx.getData()));
        txNode.put("nonce", HexUtils.toQuantityJsonHex(tx.getNonceAsInteger()));
        txNode.put("to", HexUtils.toJsonHex(tx.getReceiveAddress().getBytes()));
        txNode.put("value", HexUtils.toJsonHex(tx.getValue().getBytes()));

        return txNode;
    }

    private JsonNode summarySerializer(Transaction tx) {
        String summaryFormatted = String.format("%s: %s wei + %d x %s gas",
                tx.getReceiveAddress().toString(),
                tx.getValue().toString(),
                tx.getGasLimitAsInteger(),
                tx.getGasPrice().toString());
        return jsonNodeFactory.textNode(summaryFormatted);
    }

    private Map<RskAddress, Map<BigInteger, List<Transaction>>> groupTransactions(List<Transaction> transactions) {
        Map<RskAddress, Map<BigInteger, List<Transaction>>> groupedTransactions = new HashMap<>();
        for (Transaction tx : transactions){
            Map<BigInteger, List<Transaction>> txsBySender = groupedTransactions.get(tx.getSender());
            if (txsBySender == null){
                txsBySender = new HashMap<>();
                groupedTransactions.put(tx.getSender(), txsBySender);
            }
            List<Transaction> txsByNonce = txsBySender.get(tx.getNonceAsInteger());
            if (txsByNonce == null){
                List<Transaction> txs = new ArrayList<>();
                txs.add(tx);
                txsBySender.put(tx.getNonceAsInteger(), txs);
            } else {
                txsByNonce.add(tx);
            }
        }
        return groupedTransactions;
    }

    /**
     * This method should return 2 dictionaries containing pending and queued transactions
     * Each entry is an origin-address to a batch of scheduled transactions
     * These batches themselves are maps associating nonces with transactions summary strings.
     * When there are no transactions the answer would be
     * "{"pending": {}, "queued": {}}"
     */
    @Override
    public JsonNode inspect() {
        Map<String, JsonNode> contentProps = new HashMap<>();
        Map<RskAddress, Map<BigInteger, List<Transaction>>> pendingGrouped = groupTransactions(transactionPool.getPendingTransactions());
        Map<RskAddress, Map<BigInteger, List<Transaction>>> queuedGrouped = groupTransactions(transactionPool.getQueuedTransactions());
        contentProps.put(PENDING, serializeTransactions(pendingGrouped, this::summarySerializer));
        contentProps.put(QUEUED, serializeTransactions(queuedGrouped, this::summarySerializer));
        JsonNode node = jsonNodeFactory.objectNode().setAll(contentProps);
        return node;
    }

    /**
     * This method should return 2 integers for pending and queued transactions
     * These value represents
     * the number of transactions currently pending for inclusion in the next block(s),
     * as well as the ones that are being scheduled for future execution only.
     * "{"pending": 0, "queued": 0}"
     */
    @Override
    public JsonNode status() {
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put(PENDING, jsonNodeFactory.numberNode(transactionPool.getPendingTransactions().size()));
        txProps.put(QUEUED, jsonNodeFactory.numberNode(transactionPool.getQueuedTransactions().size()));
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node;
    }
}