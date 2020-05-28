/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.*;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Julian Len and Sebastian Sicardi on 21/04/20.
 */
public class LightClientMessageVisitor {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");
    private final LightPeer lightPeer;
    private final LightSyncProcessor lightSyncProcessor;
    private final LightProcessor lightProcessor;
    private final LightClientHandler handler;
    private final ChannelHandlerContext ctx;

    public LightClientMessageVisitor(LightPeer lightPeer, LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor,
                                     ChannelHandlerContext ctx, LightClientHandler handler) {
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
        this.lightPeer = lightPeer;
        this.handler = handler;
        this.ctx = ctx;
    }

    public void apply(StatusMessage msg) {
        logger.debug("Read message: {} STATUS", msg);
        lightSyncProcessor.processStatusMessage(msg, lightPeer, ctx, handler);
    }

    public void apply(GetBlockReceiptsMessage msg) {
        logger.debug("Read message: {} GET_BLOCK_RECEIPTS", msg);
        lightProcessor.processGetBlockReceiptsMessage(msg.getId(), msg.getBlockHash(), lightPeer);
    }

    public void apply(BlockReceiptsMessage msg) {
        logger.debug("Read message: {} BLOCK_RECEIPTS", msg);
        lightProcessor.processBlockReceiptsMessage(msg.getId(), msg.getBlockReceipts(), lightPeer);
    }

    public void apply(GetTransactionIndexMessage msg){
        logger.debug("Read message: {} GET_TRANSACTION_INDEX", msg);
        lightProcessor.processGetTransactionIndex(msg.getId(), msg.getTxHash(), lightPeer);
    }

    public void apply(TransactionIndexMessage msg){
        logger.debug("Read message: {} TRANSACTION_INDEX", msg);
        lightProcessor.processTransactionIndexMessage(msg.getId(), msg.getBlockNumber(),
                msg.getBlockHash(), msg.getTransactionIndex(), lightPeer);
    }

    public void apply(GetCodeMessage msg){
        logger.debug("Read message: {} GET_CODE", msg);
        lightProcessor.processGetCodeMessage(msg.getId(), msg.getBlockHash(), msg.getAddress(), lightPeer);
    }

    public void apply(CodeMessage msg){
        logger.debug("Read message: {} CODE", msg);
        lightProcessor.processCodeMessage(msg.getId(), msg.getCodeHash(), lightPeer);
    }

    public void apply(GetAccountsMessage msg){
        logger.debug("Read message: {} GET_ACCOUNTS", msg);
        lightProcessor.processGetAccountsMessage(msg.getId(), msg.getBlockHash(), msg.getAddressHash(), lightPeer);
    }

    public void apply(AccountsMessage msg){
        logger.debug("Read message: {} ACCOUNTS", msg);
        lightProcessor.processAccountsMessage(msg.getId(), msg.getMerkleInclusionProof(),
                msg.getNonce(), msg.getBalance(), msg.getCodeHash(), msg.getStorageRoot(), lightPeer);
    }

    public void apply(GetBlockHeaderMessage msg) {
        logger.debug("Read message: {} GET_BLOCK_HEADER", msg);
        lightProcessor.processGetBlockHeaderMessage(msg.getId(), msg.getBlockHash(), lightPeer);
    }

    public void apply(BlockHeaderMessage msg) {
        logger.debug("Read message: {} BLOCK_HEADER", msg);
        lightSyncProcessor.processBlockHeaderMessage(msg.getId(), msg.getBlockHeader(), lightPeer);
    }

    public void apply(GetBlockBodyMessage msg) {
        logger.debug("Read message: {} GET_BLOCK_BODY", msg);
        lightProcessor.processGetBlockBodyMessage(msg.getId(), msg.getBlockHash(), lightPeer);
    }

    public void apply(BlockBodyMessage msg) {
        logger.debug("Read message: {} BLOCK_BODY", msg);
        lightProcessor.processBlockBodyMessage(msg.getId(), msg.getUncles(), msg.getTransactions(), lightPeer);
    }

    public void apply(GetStorageMessage msg) {
        logger.debug("Read message: {} GET_STORAGE", msg);
        lightProcessor.processGetStorageMessage(msg.getId(), msg.getBlockHash(), msg.getAddressHash(),
                msg.getStorageKeyHash(), lightPeer);
    }

    public void apply(StorageMessage msg) {
        logger.debug("Read message: {} STORAGE", msg);
        lightProcessor.processStorageMessage(msg.getId(), msg.getMerkleInclusionProof(), msg.getStorageValue(), lightPeer);
    }
}

