package org.ethereum.listener;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;

import java.util.List;

public final class NoOpEthereumListener implements EthereumListener {

    public static final NoOpEthereumListener INSTANCE = new NoOpEthereumListener();

    private NoOpEthereumListener() {}

    @Override public void trace(String output) {}
    @Override public void onNodeDiscovered(Node node) {}
    @Override public void onHandShakePeer(Channel channel, HelloMessage helloMessage) {}
    @Override public void onEthStatusUpdated(Channel channel, StatusMessage status) {}
    @Override public void onRecvMessage(Channel channel, Message message) {}
    @Override public void onBlock(Block block, List<TransactionReceipt> receipts) {}
    @Override public void onBestBlock(Block block, List<TransactionReceipt> receipts) {}
    @Override public void onPeerDisconnect(String host, long port) {}
    @Override public void onPendingTransactionsReceived(List<Transaction> transactions) {}
    @Override public void onTransactionPoolChanged(TransactionPool transactionPool) {}
    @Override public void onNoConnections() {}
    @Override public void onPeerAddedToSyncPool(Channel peer) {}
    @Override public void onLongSyncDone() {}
    @Override public void onLongSyncStarted() {}
}
