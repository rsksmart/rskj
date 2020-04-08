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
    private LightProcessor lightProcessor;

    public LightClientHandler(MessageQueue msgQueue, LightProcessor lightProcessor) {
        this.msgQueue = msgQueue;
        this.lightProcessor = lightProcessor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LightClientMessage msg) throws Exception {
        switch (msg.getCommand()) {
            case TEST:
                logger.debug("Read message: {} TEST", msg);
                lightProcessor.processTestMessage((TestMessage) msg, msgQueue);
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
        sendTest();
    }

    private void sendTest() {
        TestMessage testMessage = new TestMessage();
        msgQueue.sendMessage(new TestMessage());
        logger.info("LC [ Sending Message {} ]", testMessage.getCommand());
    }

    public interface Factory {
        LightClientHandler newInstance(MessageQueue messageQueue);
    }
}
