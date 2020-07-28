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
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.GetBlockHeadersByHashMessage;
import co.rsk.net.light.message.GetBlockHeadersByNumberMessage;
import co.rsk.net.light.message.GetBlockHeadersMessage;
import co.rsk.net.light.message.StatusMessage;
import co.rsk.net.light.state.*;
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
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static co.rsk.net.light.LightClientMessageCodes.*;


public class LightSyncProcessor {

    private static final int MAX_PENDING_MESSAGES = 10;
    private static final int MAX_PEER_CONNECTIONS = 1;
    public static final int MAX_REQUESTED_HEADERS = 192; //Based in max_chunks, this number should be in the config file in some light section
    private final LightPeersInformation lightPeersInformation;
    private LightSyncState syncState;
    private final SystemProperties config;
    private final Genesis genesis;
    private final BlockStore blockStore;
    private final Blockchain blockchain;
    private final byte version;
    private static final Logger loggerNet = LoggerFactory.getLogger("lightnet");
    private long lastRequestedId;
    private final Map<Long, LightClientMessageCodes> pendingMessages;
    private final ProofOfWorkRule blockHeaderValidationRule;
    private final List<BlockHeader> downloadedHeaders;

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
        this.syncState = new IdleSyncState();
        this.downloadedHeaders = new ArrayList<>();
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

        lightPeersInformation.registerLightPeer(lightPeer, status, msg.isTxRelay());

        final Optional<LightPeer> bestPeer = lightPeersInformation.getBestPeer(blockchain);

        if (!bestPeer.isPresent()) {
            return;
        }

        startSync(lightPeer, blockchain.getBestBlock().getHeader());
    }

    public void sendStatusMessage(LightPeer lightPeer) {
        Block block = blockStore.getBestBlock();
        LightStatus status = getCurrentStatus(block);
        StatusMessage statusMessage = new StatusMessage(0L, status, false);

        lightPeer.sendMessage(statusMessage);

        loggerNet.trace("Sending status best block {} to {}",
                block.getNumber(), lightPeer.getPeerIdShort());
    }

    public void sendBlockHeadersByHashMessage(LightPeer lightPeer, byte[] startBlockHash, int maxAmountOfHeaders, int skip, boolean reverse) {
        GetBlockHeadersByHashMessage blockHeaderMessage = new GetBlockHeadersByHashMessage(++lastRequestedId, startBlockHash, maxAmountOfHeaders, skip, reverse);
        sendMessage(lightPeer, blockHeaderMessage);
    }

    public void sendBlockHeadersByNumberMessage(LightPeer lightPeer, long startBlockNumber, int maxAmountOfHeaders, int skip, boolean reverse) {
        GetBlockHeadersByNumberMessage blockHeaderMessage = new GetBlockHeadersByNumberMessage(++lastRequestedId, startBlockNumber, maxAmountOfHeaders, skip, reverse);
        sendMessage(lightPeer, blockHeaderMessage);
    }

    private void sendMessage(LightPeer lightPeer, GetBlockHeadersMessage blockHeaderMessage) {
        pendingMessages.put(lastRequestedId, BLOCK_HEADER);
        lightPeer.sendMessage(blockHeaderMessage);
    }

    public void processBlockHeadersMessage(long id, List<BlockHeader> blockHeaders, LightPeer lightPeer) {
        if (!isPending(id, BLOCK_HEADER)) {
            notPendingMessage();
            //TODO: Abort process
            return;
        }

        if (blockHeaders.isEmpty() || blockHeaders.size() > MAX_REQUESTED_HEADERS) {
            wrongBlockHeadersSize();
            //TODO: Abort process
            return;
        }

        for (BlockHeader h : blockHeaders) {
            if (!blockHeaderValidationRule.isValid(h)) {
                invalidPoW();
                //TODO: Abort process
                return;
            }
        }

        pendingMessages.remove(id, BLOCK_HEADER);
        lightPeer.receivedBlockHeaders(blockHeaders);
        syncState.newBlockHeaders(lightPeer, blockHeaders);

    }

    public void startAncestorSearchFrom(LightPeer lightPeer, long bestBlockNumber) {
        setState(new CommonAncestorSearchSyncState(this, lightPeer, bestBlockNumber, blockchain));
    }

    public void startSync(LightPeer lightPeer, BlockHeader bestBlockHeader) {
        setState(new DecidingLightSyncState(this, lightPeer, bestBlockHeader));
    }

    public void startSyncRound(LightPeer lightPeer, BlockHeader startBlockHeader) {
        final LightStatus lightStatus = lightPeersInformation.getLightStatus(lightPeer);

        if (startBlockHeader.getDifficulty().compareTo(lightStatus.getTotalDifficulty()) > 0) {
            wrongDifficulty();
            return;
        }

        setState(new StartRoundSyncState(this, lightPeer, startBlockHeader, lightStatus.getBestNumber()));
    }

    public LightSyncState getSyncState() {
        return syncState;
    }

    public boolean isCorrect(List<BlockHeader> blockHeaders, int maxAmountOfHeaders, long startBlockNumber, int skip, boolean reverse) {
        if (blockHeaders.get(0).getNumber() != startBlockNumber) {
            differentFirstBlocks();
            //TODO: Abort process
            return false;
        }

        return hasCorrectAmountAndSkip(blockHeaders, maxAmountOfHeaders, skip, reverse);
    }

    public boolean isCorrect(List<BlockHeader> blockHeaders, int maxAmountOfHeaders, byte[] startBlockHash, int skip, boolean reverse) {

        if (!ByteUtil.fastEquals(blockHeaders.get(0).getHash().getBytes(), startBlockHash)) {
            differentFirstBlocks();
            //TODO: Abort process
            return false;
        }

        return hasCorrectAmountAndSkip(blockHeaders, maxAmountOfHeaders, skip, reverse);
    }

    public void addDownloadedHeaders(List<BlockHeader> bhs) {
        downloadedHeaders.addAll(bhs);
    }

    public List<BlockHeader> getDownloadedHeaders() {
        return new ArrayList<>(downloadedHeaders);
    }

    private boolean hasCorrectAmountAndSkip(List<BlockHeader> blockHeaders, int maxAmountOfHeaders, int skip, boolean reverse) {
        if (blockHeaders.size() > maxAmountOfHeaders) {
            moreBlocksThanAllowed();
            //TODO: Abort process
            return false;
        }


        if (!isCorrectSkipped(blockHeaders, skip, reverse)) {
            incorrectSkipped();
            //TODO: Abort process
            return false;
        }
        return true;
    }

    private boolean isCorrectSkipped(List<BlockHeader> blockHeaders, int skip, boolean reverse) {
        for (int i = 0; i < blockHeaders.size() - 1; i++) {
            final long first = blockHeaders.get(i).getNumber();
            final long second = blockHeaders.get(i+1).getNumber();
            if ((!reverse && first >= second) || (reverse && second >= first)) {
                return false;
            } else {
                if (Math.abs(second - first) - 1 != skip) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setState(LightSyncState syncState) {
        this.syncState = syncState;
        syncState.sync();
    }

    private boolean isPending(long id, LightClientMessageCodes code) {
        return pendingMessages.containsKey(id) && pendingMessages.get(id).asByte() == code.asByte();
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

    public void invalidPoW() {

    }

    public void wrongBlockHeadersSize() {

    }

    public void notPendingMessage() {

    }

    public void endStartRound() {
        //End sync
    }

    public void startFetchRound(LightPeer lightPeer, List<BlockHeader> sparseHeaders, long targetNumber) {
        setState(new FetchRoundSyncState(lightPeer, sparseHeaders, targetNumber, this));
        //Starting fetch sub chains
    }

    public void differentFirstBlocks() {

    }

    public void incorrectSkipped() {

    }

    public void moreBlocksThanAllowed() {

    }

    public void incorrectParentHash() {

    }

    public void wrongDifficulty() {

    }

    public void failedAttempt() {

    }

    public void badConnected() {

    }

    public void badSubchain() {

    }

    public void endSync() {

    }
}
