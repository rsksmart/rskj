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
                logger.debug("Read message: {} TEST. Sending Test response", msg);
                lightProcessor.processTestMessage((TestMessage) msg, msgQueue);
                break;
            case GET_BLOCK_RECEIPTS:
                logger.debug("Read message: {} GET_BLOCK_RECEIPTS. Sending receipts request", msg);
                GetBlockReceiptsMessage getBlockReceiptsMsg = (GetBlockReceiptsMessage) msg;
                lightProcessor.processGetBlockReceiptsMessage(getBlockReceiptsMsg.getId(), getBlockReceiptsMsg.getBlockHash(), msgQueue);
                break;
            case BLOCK_RECEIPTS:
                logger.debug("Read message: {} BLOCK_RECEIPTS. Sending receipts response", msg);
                BlockReceiptsMessage blockReceiptsMsg = (BlockReceiptsMessage) msg;
                lightProcessor.processBlockReceiptsMessage(blockReceiptsMsg.getId(), blockReceiptsMsg.getBlockReceipts(), msgQueue);
            case GET_TRANSACTION_INDEX:
                logger.debug("Read message: {} GET_TRANSACTION_INDEX.", msg);
                GetTransactionIndex getTxIndexMsg = GetTransactionIndex.decode(msg.getEncoded());
                lightProcessor.processGetTransactionIndex(msgQueue, getTxIndexMsg.getId(), getTxIndexMsg.getTxHash());
                break;
            case TRANSACTION_INDEX:
                logger.debug("Read message: {} TRANSACTION_INDEX.", msg);
                TransactionIndex txIndexMsg = TransactionIndex.decode(msg.getEncoded());
                lightProcessor.processTransactionIndexMessage(msgQueue, txIndexMsg);
                break;
            case GET_CODE:
                logger.debug("Read message: {} GET_CODE. Sending code request", msg);
                GetCodeMessage getCodeMsg = (GetCodeMessage) msg;
                lightProcessor.processGetCodeMessage(getCodeMsg.getId(), getCodeMsg.getBlockHash(), getCodeMsg.getAddress(), msgQueue);
                break;
            case CODE:
                logger.debug("Read message: {} CODE. Sending code response", msg);
                CodeMessage codeMsg = (CodeMessage) msg;
                lightProcessor.processCodeMessage(codeMsg.getId(), codeMsg.getCodeHash(), msgQueue);
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
