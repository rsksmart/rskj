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
    private final LightPeersInformation lightPeersInformation;
    private SystemProperties config;
    private final Genesis genesis;
    private final BlockStore blockStore;
    private Blockchain blockchain;
    private final byte version;
    private static final Logger loggerNet = LoggerFactory.getLogger("lightnet");
    private long lastRequestedId;
    private final Map<Long, LightClientMessageCodes> pendingMessages;
    private ProofOfWorkRule blockHeaderValidationRule;


    public LightSyncProcessor(SystemProperties config, Genesis genesis, BlockStore blockStore, Blockchain blockchain, ProofOfWorkRule blockHeaderValidationRule, LightPeersInformation lightPeersInformation) {
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
        this.lightPeersInformation = lightPeersInformation;
    }

    public void processStatusMessage(StatusMessage msg, LightPeer lightPeer, ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        LightStatus status = msg.getStatus();
        try {
            String bestHashLog = HashUtil.shortHash(status.getBestHash());
            loggerNet.debug("Receiving Status - block {} {}", status.getBestNumber(), bestHashLog);

            if (!isCompatible(status, lightPeer, ctx, lightClientHandler)){
                return;
            }

        } catch (NoSuchElementException e) {
            loggerNet.debug("LCHandler already removed - exception: {}", e.getMessage());
        }

        if (lightPeersInformation.getConnectedPeersSize() >= MAX_PEER_CONNECTIONS) {
            return;
        }

        if (!hasLowerDifficulty(status)) {
            return;
        }

        lightPeersInformation.registerLightPeer(lightPeer, status, msg.isTxRelay());

        final Optional<LightPeer> bestPeer = lightPeersInformation.getBestPeer();

        if (!bestPeer.isPresent()) {
            return;
        }

        byte[] bestBlockHash = lightPeersInformation.getLightPeerStatus(bestPeer.get()).getBestHash();
        GetBlockHeadersMessage blockHeaderMessage = new GetBlockHeadersMessage(++lastRequestedId, bestBlockHash, 1, 0, false);
        pendingMessages.put(lastRequestedId, BLOCK_HEADER);
        lightPeer.sendMessage(blockHeaderMessage);
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

        if (blockHeaders.isEmpty()) {
            return;
        }

        //TODO: Mechanism of disconnecting when peer gives bad information
        for (BlockHeader h : blockHeaders) {
            if (!blockHeaderValidationRule.isValid(h)) {
                return;
            }
        }

        pendingMessages.remove(id, BLOCK_HEADER);
        lightPeer.receivedBlock(blockHeaders);
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

    private boolean isCompatible(LightStatus msgStatus, LightPeer lightPeer, ChannelHandlerContext ctx,
                                 LightClientHandler lightClientHandler){
        // HOW TO USE: Any new check you want to include, you should add a compareStatusParam with its
        // properties to compare
        return compareStatusParam(version, msgStatus.getProtocolVersion(),
                ReasonCode.INCOMPATIBLE_PROTOCOL, "Protocol Incompatibility",
                lightPeer, ctx, lightClientHandler) &&

        compareStatusParam(config.networkId(), msgStatus.getNetworkId(),
                ReasonCode.NULL_IDENTITY, "Invalid Network",
                lightPeer, ctx, lightClientHandler) &&

        compareStatusParam(genesis.getHash(), new Keccak256(msgStatus.getGenesisHash()),
                ReasonCode.UNEXPECTED_GENESIS, "Unexpected Genesis",
                lightPeer, ctx, lightClientHandler);
    }

    private boolean compareStatusParam(Object expectedParam, Object msgParam, ReasonCode reason, String reasonText,
                                       LightPeer lightPeer, ChannelHandlerContext ctx, LightClientHandler lightClientHandler){
        if (!expectedParam.equals(msgParam)) {
            loggerNet.info("Client expected {} - but was {}", expectedParam, msgParam);
            loggerNet.info("Removing LCHandler for {} reason: {}", ctx.channel().remoteAddress(), reasonText);
            lightPeer.disconnect(reason);
            ctx.pipeline().remove(lightClientHandler);
            return false;
        }
        return true;
    }
}
