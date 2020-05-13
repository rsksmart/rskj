/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.GetBlockHeadersMessage;
import co.rsk.net.light.message.StatusMessage;
import co.rsk.validators.ProofOfWorkRule;
import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.net.message.ReasonCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static co.rsk.net.light.LightClientMessageCodes.*;


public class LightSyncProcessor {

    private static final int MAX_PENDING_MESSAGES = 1;
    private static final int MAX_PEER_CONNECTIONS = 1;
    private SystemProperties config;
    private final Genesis genesis;
    private final BlockStore blockStore;
    private Blockchain blockchain;
    private final byte version;
    private static final Logger loggerNet = LoggerFactory.getLogger("lightnet");
    private Map<LightPeer, LightStatus> peerStatuses = new HashMap<>();
    private Map<LightPeer, Boolean> txRelay = new HashMap<>();
    private long lastRequestedId;
    private final Map<Long, LightClientMessageCodes> pendingMessages;
    private ProofOfWorkRule blockHeaderValidationRule;
    private LightSyncState state;


    public LightSyncProcessor(SystemProperties config, Genesis genesis, BlockStore blockStore, Blockchain blockchain, ProofOfWorkRule blockHeaderValidationRule) {
        this.config = config;
        this.genesis = genesis;
        this.blockStore = blockStore;
        this.blockchain = blockchain;
        this.blockHeaderValidationRule = blockHeaderValidationRule;
        this.version = (byte) 0;
        this.pendingMessages = new LinkedHashMap<Long, LightClientMessageCodes>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, LightClientMessageCodes> eldest) {
                boolean shouldDiscard = size() > MAX_PENDING_MESSAGES;
                if (shouldDiscard) {
                    loggerNet.trace("Pending {}@{} DISCARDED", eldest.getValue(), eldest.getKey());
                }
                return shouldDiscard;
            }
        };
        this.state = new BaseLightSyncState();
    }

    public void processStatusMessage(StatusMessage msg, LightPeer lightPeer, ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        LightStatus status = msg.getStatus();
        try {
            String bestHashLog = HashUtil.shortHash(status.getBestHash());
            loggerNet.debug("Receiving Status - block {} {}", status.getBestNumber(), bestHashLog);

            byte protocolVersion = status.getProtocolVersion();
            if (protocolVersion != version) {
                loggerNet.info("Removing LCHandler for {} due to protocol incompatibility", ctx.channel().remoteAddress());
                loggerNet.info("Protocol version {} - message protocol version {}",
                        version,
                        protocolVersion);
                lightPeer.disconnect(ReasonCode.INCOMPATIBLE_PROTOCOL);
                ctx.pipeline().remove(lightClientHandler); // Peer is not compatible for the 'lc' sub-protocol
                return;
            }

            int networkId = config.networkId();
            int msgNetworkId = status.getNetworkId();
            if (msgNetworkId != networkId) {
                loggerNet.info("Removing LCHandler for {} due to invalid network", ctx.channel().remoteAddress());
                loggerNet.info("Different network received: config network ID {} - message network ID {}",
                        networkId, msgNetworkId);
                lightPeer.disconnect(ReasonCode.NULL_IDENTITY);
                ctx.pipeline().remove(lightClientHandler);
                return;
            }

            Keccak256 genesisHash = genesis.getHash();
            Keccak256 msgGenesisHash = new Keccak256(status.getGenesisHash());
            if (!msgGenesisHash.equals(genesisHash)) {
                loggerNet.info("Removing LCHandler for {} due to unexpected genesis", ctx.channel().remoteAddress());
                loggerNet.info("Config genesis hash {} - message genesis hash {}",
                        genesisHash, msgGenesisHash);
                lightPeer.disconnect(ReasonCode.UNEXPECTED_GENESIS);
                ctx.pipeline().remove(lightClientHandler);
                return;
            }
        } catch (NoSuchElementException e) {
            loggerNet.debug("LCHandler already removed - exception: {}", e.getMessage());
        }

        if (peerStatuses.size() >= MAX_PEER_CONNECTIONS) {
            return;
        }

        if (!hasLowerDifficulty(status)) {
            return;
        }

        peerStatuses.put(lightPeer, status);

        if (msg.isTxRelay()) {
            txRelay.put(lightPeer, true);
        }

        byte[] bestBlockHash = status.getBestHash();
        setState(new CheckingBestHeaderLightSyncState(this, bestBlockHash, lightPeer, blockHeaderValidationRule));

    }

    public void sendGetBlockHeadersMessage(LightPeer lightPeer, byte[] bestBlockHash) {
        GetBlockHeadersMessage blockHeaderMessage = new GetBlockHeadersMessage(++lastRequestedId, bestBlockHash, 1);
        pendingMessages.put(lastRequestedId, BLOCK_HEADER);
        lightPeer.sendMessage(blockHeaderMessage);
    }

    private void setState(CheckingBestHeaderLightSyncState checkingBestHeaderLightSyncState) {
        state = checkingBestHeaderLightSyncState;
        state.onEnter();
    }

    public void sendStatusMessage(LightPeer lightPeer) {
        Block block = blockStore.getBestBlock();
        LightStatus status = getCurrentStatus(block);
        StatusMessage statusMessage = new StatusMessage(0L, status, false);

        lightPeer.sendMessage(statusMessage);

        loggerNet.trace("Sending status best block {} to {}",
                block.getNumber(), lightPeer.getPeerIdShort());
    }

    public void processBlockHeadersMessage(long id, List<BlockHeader> blockHeaders, LightPeer lightPeer) {
        if (!isPending(id, BLOCK_HEADER)) {
            return;
        }

        pendingMessages.remove(id, BLOCK_HEADER);
        state.newBlockHeaderMessage(blockHeaders);

    }

    public boolean hasTxRelay(LightPeer peer) {
        if (!txRelay.containsKey(peer)) {
            return false;
        }

        return txRelay.get(peer);
    }

    private boolean isPending(long id, LightClientMessageCodes code) {
        return pendingMessages.containsKey(id) && pendingMessages.get(id).asByte() == code.asByte();
    }

    private boolean hasLowerDifficulty(LightStatus status) {
        boolean hasTotalDifficulty = status.getTotalDifficulty() != null;
        BlockChainStatus nodeStatus = blockchain.getStatus();

        // this works only for testing purposes, real status without difficulty don't reach this far
        return  (hasTotalDifficulty && nodeStatus.hasLowerDifficultyThan(status)) ||
                (!hasTotalDifficulty && nodeStatus.getBestBlockNumber() < status.getBestNumber());
    }

    private LightStatus getCurrentStatus(Block block) {
        byte[] bestHash = block.getHash().getBytes();
        long bestNumber = block.getNumber();
        BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(bestHash);
        return new LightStatus((byte) 0, config.networkId(), totalDifficulty, bestHash, bestNumber, genesis.getHash().getBytes());
    }

    @VisibleForTesting
    public LightSyncState getSyncState() {
        return this.state;
    }
}
