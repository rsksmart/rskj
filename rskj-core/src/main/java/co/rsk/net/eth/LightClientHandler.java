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

import co.rsk.core.BlockDifficulty;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
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
    private final SystemProperties config;
    private final Genesis genesis;
    private final BlockStore blockStore;
    private LightProcessor lightProcessor;

    public LightClientHandler(MessageQueue msgQueue, LightProcessor lightProcessor, SystemProperties config, Genesis genesis, BlockStore blockStore) {
        this.msgQueue = msgQueue;
        this.lightProcessor = lightProcessor;
        this.config = config;
        this.genesis = genesis;
        this.blockStore = blockStore;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LightClientMessage msg) throws Exception {
        switch (msg.getCommand()) {
            case STATUS:
                logger.debug("Read message: {} TEST. Sending Test response", msg);
                lightProcessor.processStatusMessage((StatusMessage) msg, msgQueue);
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
        sendStatusMessage();
    }

    private void sendStatusMessage() {
        Block block = blockStore.getBestBlock();
        byte[] bestHash = block.getHash().getBytes();
        long bestNumber = block.getNumber();
        BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(bestHash);
        StatusMessage statusMessage = new StatusMessage(0L, (byte) 0, config.networkId(), totalDifficulty, bestHash, bestNumber, genesis.getHash().getBytes());
        msgQueue.sendMessage(statusMessage);
        logger.info("LC [ Sending Message {} ]", statusMessage.getCommand());
    }

    public interface Factory {
        LightClientHandler newInstance(MessageQueue messageQueue);
    }
}
