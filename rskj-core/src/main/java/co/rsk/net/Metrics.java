/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.BIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.nanoTime;

@Component
public class Metrics {
    private static final Logger logger = LoggerFactory.getLogger("metrics");
    private static String nodeID;

    private static long processTxsMessageEventStart = 0;
    private static long processTxsMessageStepStart = 0;

    private static long processBlockMessageEventStart = 0;
    private static long processBlockMessageStepStart = 0;

    @Nonnull
    public static Logger logger() {
        return logger;
    }

    @Nonnull
    private static String prettyTxs(@Nonnull final List<Transaction> txs) {
        StringBuilder res = new StringBuilder();

        res.append('[');
        for( Transaction tx : txs )
        {
            if(res.length() > 1) {
                res.append("][");
            }

            String nonce = BIUtil.toBI(tx.getNonce()).toString();
            String pretty = String.format("H:%s - N:%s", HashUtil.shortHash(tx.getHash()), nonce);
            res.append(pretty);
        }
        res.append(']');

        return res.toString();
    }

    // This function should be called when a node has a new block (full).
    public static void newBlock(@Nonnull final Block block, @Nonnull final NodeID sender) {
        String event = String.format("event: %s hash: %s number: %d parent: %s sender: %s",
                "newBlock",
                HashUtil.shortHash(block.getHash()),
                block.getNumber(),
                HashUtil.shortHash(block.getParentHash()),
                HashUtil.shortHash(sender.getID())
        );
        logEvent(event);
    }

    public static void broadcastBlock(@Nonnull final Block block) {
        String event = String.format("event: %s hash: %s number: %d parent: %s",
                "broadcastBlock",
                HashUtil.shortHash(block.getHash()),
                block.getNumber(),
                HashUtil.shortHash(block.getParentHash())
        );
        logEvent(event);
    }

    public static void broadcastTransaction(@Nonnull final Transaction tx) {
        String event = String.format("event: %s hash: %s nonce: %s",
                "broadcastTransaction",
                HashUtil.shortHash(tx.getHash()),
                BIUtil.toBI(tx.getNonce()).toString()
        );
        logEvent(event);
    }

    // This function should be called when a node has a new Tx.
    public static void newTransaction(@Nonnull final Transaction tx, @Nonnull final NodeID sender) {
        String event = String.format("event: %s hash: %s nonce: %s sender: %s",
                "newTransaction",
                HashUtil.shortHash(tx.getHash()),
                BIUtil.toBI(tx.getNonce()).toString(),
                HashUtil.shortHash(sender.getID())
        );

        logEvent(event);
    }

    // This function should be called when a node has a new block header and can start mining.
    public static void newBlockHeader(@Nonnull final BlockHeader header, @Nonnull final NodeID sender) {
        String event = String.format("event: %s hash: %s number: %d parent: %s sender: %s",
                "newBlockHeader",
                HashUtil.shortHash(header.getHash()),
                header.getNumber(),
                HashUtil.shortHash(header.getParentHash()),
                HashUtil.shortHash(sender.getID())
        );
        logEvent(event);
    }

    // This function should be called when a node knows that a certain block exists,
    // but it doesn't have the full block body nor the header.
    public static void newBlockHash(@Nonnull final BlockIdentifier identifier, @Nonnull final NodeID sender) {
        String event = String.format("event: %s hash: %s number: %d sender: %s",
                "newBlockHash",
                HashUtil.shortHash(identifier.getHash()),
                identifier.getNumber(),
                HashUtil.shortHash(sender.getID())
        );

        logEvent(event);
    }

    public static void messageBytes(@Nonnull final NodeID sender, int length) {
        String event = String.format("event: %s bytes: %d sender: %s",
                "messageBytes",
                length,
                HashUtil.shortHash(sender.getID())
        );

        logEvent(event);
    }

    private static void logEvent(@Nonnull final String event) {
        logger.debug("{} at: {} nano: {} | {} ", nodeID, currentTimeMillis(), nanoTime(), event);
    }

    public static void registerNodeID(byte[] bytes) {
        nodeID = HashUtil.shortHash(bytes);
    }

    public static void rebranch(@Nonnull final Block bestBlock, @Nonnull final Block block, final int rebranchSize) {
        String event = String.format("event: %s bestBlock hash: %s number: %d prevBestBlock hash: %s number: %d size: %d",
                "rebranch",
                HashUtil.shortHash(block.getHash()),
                block.getNumber(),
                HashUtil.shortHash(bestBlock.getHash()),
                bestBlock.getNumber(),
                rebranchSize
        );

        logEvent(event);
    }

    /**
     * Successive calls to this method log metrics-information related to TransactionsMessage processing (and its steps)
     * It also keeps track of duration between successive steps as well as total duration (from "start" to "finish")
     * using static variables processTxsMessageEventStart and processTxsMessageStepStart
     */
    public static void processTxsMessage(String step, List<Transaction> txs, @Nonnull final NodeID senderNodeId) {
        long stepTime = nanoTime();

        Map<String, String> eventInfo = new HashMap<>();
        eventInfo.put("txs", prettyTxs(txs));
        eventInfo.put("senderNodeId", HashUtil.shortHash(senderNodeId.getID()));

        if ("start".equals(step)) {
            processTxsMessageEventStart = nanoTime();
            txs.stream().forEach(tx -> Metrics.newTransaction(tx, senderNodeId));
            eventInfo.put("txsReceived", String.format("%s", txs.size()));
        }
        else if ("finish".equals(step)) {
            eventInfo.put("duration", processTxsMessageEventStart == 0
                                        ? "--"
                                        : String.format("%s", nanoTime() - processTxsMessageEventStart));
        }
        else {
            eventInfo.put("duration", String.format("%s", nanoTime() - processTxsMessageStepStart));
        }

        String event = String.format("event: %s step: %s info: %s", "processTxsMessage", step, getAsJson(eventInfo));
        logEvent(event);
        processTxsMessageStepStart = stepTime;
    }

    /**
     * Successive calls to this method log metrics-information related to BlockMessage processing (and its steps).
     * It also keeps track of duration between successive steps as well as total duration (from "start" to "finish")
     * using static variables processBlockMessageEventStart and processBlockMessageStepStart
     */
    public static void processBlockMessage(String step, Block block, @Nonnull final NodeID senderNodeId) {
        long stepTime = nanoTime();

        Map<String, String> info = new HashMap<>();
        info.put("hash", HashUtil.shortHash(block.getHash()));
        info.put("number", String.format("%s", block.getNumber()));
        info.put("parent", HashUtil.shortHash(block.getParentHash()));
        info.put("senderNodeId", HashUtil.shortHash(senderNodeId.getID()));

        if ("start".equals(step)) {
            Metrics.newBlock(block, senderNodeId);
            processBlockMessageEventStart = stepTime;
        } else if ("finish".equals(step)) {
            info.put("duration", processBlockMessageEventStart == 0 ?
                                    "--"
                                    : String.format("%s", nanoTime() - processBlockMessageEventStart));
        } else {
            info.put("duration", String.format("%s", nanoTime() - processBlockMessageStepStart));
        }

        String event = String.format("event: %s step: %s info: %s", "processBlockMessage", step, getAsJson(info));
        logEvent(event);
        processBlockMessageStepStart = stepTime;
    }

    private static String getAsJson(Map<String, String> map) {

        String result = "{}";

        try {
            result = new ObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            logger.debug("Exception when serializing to json", e);
        }

        return result;
    }
}
