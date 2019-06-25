/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net;

import static java.lang.Math.min;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.util.ByteUtil;
import org.mapdb.Serializer;

/**
 * Handles all possible statistics related to a Node The primary aim of this is collecting info
 * about a Node for maintaining its reputation.
 *
 * <p>Created by Anton Nashatyrev on 16.07.2015.
 */
public class NodeStatistics {
    public static final int REPUTATION_PREDEFINED = 1000500;

    public class StatHandler {
        AtomicInteger count = new AtomicInteger(0);

        public void add() {
            count.incrementAndGet();
        }

        public int get() {
            return count.get();
        }

        public String toString() {
            return count.toString();
        }
    }

    static class Persistent implements Serializable {
        private static final long serialVersionUID = -1246930309060559921L;
        static final Serializer<Persistent> MapDBSerializer =
                new Serializer<Persistent>() {
                    @Override
                    public void serialize(DataOutput out, Persistent value) throws IOException {
                        out.writeInt(value.reputation);
                    }

                    @Override
                    public Persistent deserialize(DataInput in, int available) throws IOException {
                        Persistent persistent = new Persistent();
                        persistent.reputation = in.readInt();
                        return persistent;
                    }
                };
        int reputation;
    }

    private boolean isPredefined = false;

    private int savedReputation = 0;

    // discovery stat
    public final StatHandler discoverOutPing = new StatHandler();
    public final StatHandler discoverInPong = new StatHandler();
    public final StatHandler discoverOutPong = new StatHandler();
    public final StatHandler discoverInPing = new StatHandler();
    public final StatHandler discoverInFind = new StatHandler();
    public final StatHandler discoverOutFind = new StatHandler();
    public final StatHandler discoverInNeighbours = new StatHandler();
    public final StatHandler discoverOutNeighbours = new StatHandler();
    public final AtomicLong lastPongReplyTime = new AtomicLong(0l); // in milliseconds

    // rlpx stat
    public final StatHandler rlpxConnectionAttempts = new StatHandler();
    public final StatHandler rlpxAuthMessagesSent = new StatHandler();
    public final StatHandler rlpxOutHello = new StatHandler();
    public final StatHandler rlpxInHello = new StatHandler();
    public final StatHandler rlpxHandshake = new StatHandler();
    public final StatHandler rlpxOutMessages = new StatHandler();
    public final StatHandler rlpxInMessages = new StatHandler();

    private String clientId = "";

    private ReasonCode rlpxLastRemoteDisconnectReason = null;
    private ReasonCode rlpxLastLocalDisconnectReason = null;
    private boolean disconnected = false;

    // Eth stat
    public final StatHandler ethHandshake = new StatHandler();
    public final StatHandler ethInbound = new StatHandler();
    public final StatHandler ethOutbound = new StatHandler();
    private StatusMessage ethLastInboundStatusMsg = null;
    private BigInteger ethTotalDifficulty = BigInteger.ZERO;

    int getSessionReputation() {
        return getSessionFairReputation() + (isPredefined ? REPUTATION_PREDEFINED : 0);
    }

    int getSessionFairReputation() {
        int discoverReput = 0;

        discoverReput +=
                min(discoverInPong.get(), 10)
                        * (discoverOutPing.get() == discoverInPong.get() ? 2 : 1);
        discoverReput += min(discoverInNeighbours.get(), 10) * 2;

        int rlpxReput = 0;
        rlpxReput += rlpxAuthMessagesSent.get() > 0 ? 10 : 0;
        rlpxReput += rlpxHandshake.get() > 0 ? 20 : 0;
        rlpxReput += min(rlpxInMessages.get(), 10) * 3;

        if (disconnected) {
            if (rlpxLastLocalDisconnectReason == null && rlpxLastRemoteDisconnectReason == null) {
                // means connection was dropped without reporting any reason - bad
                rlpxReput *= 0.3;
            } else if (rlpxLastLocalDisconnectReason != ReasonCode.REQUESTED) {
                // the disconnect was not initiated by discover mode
                if (rlpxLastRemoteDisconnectReason == ReasonCode.TOO_MANY_PEERS) {
                    // The peer is popular, but we were unlucky
                    rlpxReput *= 0.8;
                } else {
                    // other disconnect reasons
                    rlpxReput *= 0.5;
                }
            }
        }

        return discoverReput + 100 * rlpxReput;
    }

    public int getReputation() {
        return savedReputation / 2 + getSessionReputation();
    }

    public void nodeDisconnectedRemote(ReasonCode reason) {
        rlpxLastRemoteDisconnectReason = reason;
    }

    public void nodeDisconnectedLocal(ReasonCode reason) {
        rlpxLastLocalDisconnectReason = reason;
    }

    public void disconnected() {
        disconnected = true;
    }

    public void ethHandshake(StatusMessage ethInboundStatus) {
        this.ethLastInboundStatusMsg = ethInboundStatus;
        this.ethTotalDifficulty = ethInboundStatus.getTotalDifficultyAsBigInt();
        ethHandshake.add();
    }

    public BigInteger getEthTotalDifficulty() {
        return ethTotalDifficulty;
    }

    public void setEthTotalDifficulty(BigInteger ethTotalDifficulty) {
        this.ethTotalDifficulty = ethTotalDifficulty;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setPredefined(boolean isPredefined) {
        this.isPredefined = isPredefined;
    }

    public boolean isPredefined() {
        return isPredefined;
    }

    public StatusMessage getEthLastInboundStatusMsg() {
        return ethLastInboundStatusMsg;
    }

    Persistent getPersistent() {
        Persistent persistent = new Persistent();
        persistent.reputation = (getSessionFairReputation() + savedReputation) / 2;
        return persistent;
    }

    void setPersistedData(Persistent persistedData) {
        savedReputation = persistedData.reputation;
    }

    @Override
    public String toString() {
        return "NodeStat[reput: "
                + getReputation()
                + "("
                + savedReputation
                + "), discover: "
                + discoverInPong
                + "/"
                + discoverOutPing
                + " "
                + discoverOutPong
                + "/"
                + discoverInPing
                + " "
                + discoverInNeighbours
                + "/"
                + discoverOutFind
                + " "
                + discoverOutNeighbours
                + "/"
                + discoverInFind
                + " "
                + ", rlpx: "
                + rlpxHandshake
                + "/"
                + rlpxAuthMessagesSent
                + "/"
                + rlpxConnectionAttempts
                + " "
                + rlpxInMessages
                + "/"
                + rlpxOutMessages
                + ", eth: "
                + ethHandshake
                + "/"
                + ethInbound
                + "/"
                + ethOutbound
                + " "
                + (ethLastInboundStatusMsg != null
                        ? ByteUtil.toHexString(ethLastInboundStatusMsg.getTotalDifficulty())
                        : "-")
                + " "
                + (disconnected ? "X " : "")
                + (rlpxLastLocalDisconnectReason != null
                        ? ("<=" + rlpxLastLocalDisconnectReason)
                        : " ")
                + (rlpxLastRemoteDisconnectReason != null
                        ? ("=>" + rlpxLastRemoteDisconnectReason)
                        : " ")
                + "["
                + clientId
                + "]";
    }
}
