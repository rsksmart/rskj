package co.rsk.net.light;

import co.rsk.net.BlockSyncService;
import co.rsk.net.MessageChannel;
import co.rsk.net.messages.BlockReceiptsResponseMessage;
import co.rsk.net.messages.Message;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.TransactionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;

public class LightProcessor {
    private static final Logger logger = LoggerFactory.getLogger("lightprocessor");
    // keep tabs on which nodes know which blocks.
    private final BlockSyncService blockSyncService;
    private final Blockchain blockchain;

    public LightProcessor(@Nonnull final Blockchain blockchain,
                          @Nonnull final BlockSyncService blockSyncService) {
        this.blockSyncService = blockSyncService;
        this.blockchain = blockchain;
    }

    /**
     * processBlockReceiptsRequest sends the requested block receipts if it is available.
     *
     * @param sender the sender of the BlockReceipts message.
     * @param requestId the id of the request
     * @param blockHash   the requested block hash.
     */
    public void processBlockReceiptsRequest(MessageChannel sender, long requestId, byte[] blockHash) {
        logger.trace("Processing block receipts request {} block {} from {}", requestId, Hex.toHexString(blockHash), sender.getPeerNodeID());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        List<TransactionReceipt> receipts = new LinkedList<>();

        for (Transaction tx :  block.getTransactionsList()) {
            TransactionInfo txInfo = blockchain.getTransactionInfo(tx.getHash().getBytes());
            receipts.add(txInfo.getReceipt());
        }

        Message responseMessage = new BlockReceiptsResponseMessage(requestId, receipts);
        sender.sendMessage(responseMessage);
    }

    public void processBlockReceiptsResponse(MessageChannel sender, BlockReceiptsResponseMessage message) {
        throw new UnsupportedOperationException();
    }
}
