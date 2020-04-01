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

package co.rsk.net.eth;

import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.LightSyncProcessor;
import co.rsk.net.light.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.net.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the equivalent to the RSKWireProtocol, but both classes are
 * really Handlers for the communication channel
 */

public class LightClientHandler extends SimpleChannelInboundHandler<LightClientMessage> {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");
    private final MessageQueue msgQueue;
    private LightSyncProcessor lightSyncProcessor;
    private LightProcessor lightProcessor;

    public LightClientHandler(MessageQueue msgQueue, LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor) {
        this.msgQueue = msgQueue;
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LightClientMessage msg) {
        switch (msg.getCommand()) {
            case STATUS:
                logger.debug("Read message: {} STATUS. Sending Test response", msg);
                lightSyncProcessor.processStatusMessage((StatusMessage) msg, msgQueue, ctx, this);
                break;
            case GET_BLOCK_RECEIPTS:
                logger.debug("Read message: {} GET_BLOCK_RECEIPTS", msg);
                GetBlockReceiptsMessage getBlockReceiptsMsg = (GetBlockReceiptsMessage) msg;
                lightProcessor.processGetBlockReceiptsMessage(getBlockReceiptsMsg.getId(), getBlockReceiptsMsg.getBlockHash(), msgQueue);
                break;
            case BLOCK_RECEIPTS:
                logger.debug("Read message: {} BLOCK_RECEIPTS", msg);
                BlockReceiptsMessage blockReceiptsMsg = (BlockReceiptsMessage) msg;
                lightProcessor.processBlockReceiptsMessage(blockReceiptsMsg.getId(), blockReceiptsMsg.getBlockReceipts(), msgQueue);
                break;
            case GET_TRANSACTION_INDEX:
                logger.debug("Read message: {} GET_TRANSACTION_INDEX.", msg);
                GetTransactionIndexMessage getTxIndexMsg = new GetTransactionIndexMessage(msg.getEncoded());
                lightProcessor.processGetTransactionIndex(getTxIndexMsg.getId(), getTxIndexMsg.getTxHash(), msgQueue);
                break;
            case TRANSACTION_INDEX:
                logger.debug("Read message: {} TRANSACTION_INDEX.", msg);
                TransactionIndexMessage txIndexMsg = new TransactionIndexMessage(msg.getEncoded());
                lightProcessor.processTransactionIndexMessage(txIndexMsg.getId(), txIndexMsg.getBlockNumber(),
                        txIndexMsg.getBlockHash(), txIndexMsg.getTransactionIndex(), msgQueue);
                break;
            case GET_CODE:
                logger.debug("Read message: {} GET_CODE", msg);
                GetCodeMessage getCodeMsg = (GetCodeMessage) msg;
                lightProcessor.processGetCodeMessage(getCodeMsg.getId(), getCodeMsg.getBlockHash(), getCodeMsg.getAddress(), msgQueue);
                break;
            case CODE:
                logger.debug("Read message: {} CODE", msg);
                CodeMessage codeMsg = (CodeMessage) msg;
                lightProcessor.processCodeMessage(codeMsg.getId(), codeMsg.getCodeHash(), msgQueue);
                break;
            case GET_ACCOUNTS:
                logger.debug("Read message: {} GET_ACCOUNTS.", msg);
                GetAccountsMessage getAccountsMsg = (GetAccountsMessage) msg;
                lightProcessor.processGetAccountsMessage(getAccountsMsg.getId(), getAccountsMsg.getBlockHash(), getAccountsMsg.getAddressHash(), msgQueue);
                break;
            case ACCOUNTS:
                logger.debug("Read message: {} ACCOUNTS.", msg);
                AccountsMessage accountsMsg = (AccountsMessage) msg;
                lightProcessor.processAccountsMessage(accountsMsg.getId(), accountsMsg.getMerkleInclusionProof(),
                        accountsMsg.getNonce(), accountsMsg.getBalance(), accountsMsg.getCodeHash(),
                        accountsMsg.getStorageRoot(), msgQueue);
                break;
            case GET_BLOCK_HEADER:
                logger.debug("Read message: {} GET_BLOCK_HEADER", msg);
                GetBlockHeaderMessage getBlockHeaderMessage = (GetBlockHeaderMessage) msg;
                lightProcessor.processGetBlockHeaderMessage(getBlockHeaderMessage.getId(), getBlockHeaderMessage.getBlockHash(), msgQueue);
                break;
            case BLOCK_HEADER:
                logger.debug("Read message: {} BLOCK_HEADER", msg);
                BlockHeaderMessage blockHeaderMessage = (BlockHeaderMessage) msg;
                lightProcessor.processBlockHeaderMessage(blockHeaderMessage.getId(), blockHeaderMessage.getBlockHeader(), msgQueue);
                break;
            default:
                break;
        }

    }

    public void activate() {
        lightSyncProcessor.sendStatusMessage(msgQueue);
    }

    public interface Factory {
        LightClientHandler newInstance(MessageQueue messageQueue);
    }
}
