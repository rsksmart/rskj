package co.rsk.net.light;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.StatusMessage;
import io.netty.channel.ChannelHandlerContext;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.net.message.ReasonCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

public class LightSyncProcessor {

    private SystemProperties config;
    private final Genesis genesis;
    private final BlockStore blockStore;
    private final byte version;
    private static final Logger loggerNet = LoggerFactory.getLogger("lightnet");



    public LightSyncProcessor(SystemProperties config, Genesis genesis, BlockStore blockStore) {
        this.config = config;
        this.genesis = genesis;
        this.blockStore = blockStore;
        this.version = (byte) 0;
    }

    public void processStatusMessage(StatusMessage msg, LightPeer lightPeer, ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        try {
            loggerNet.debug("Receiving Status - block {} {}", msg.getBestNumber(), HashUtil.shortHash(msg.getBestHash()));

            byte protocolVersion = msg.getProtocolVersion();
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
            int msgNetworkId = msg.getNetworkId();
            if (msgNetworkId != networkId) {
                loggerNet.info("Removing LCHandler for {} due to invalid network", ctx.channel().remoteAddress());
                loggerNet.info("Different network received: config network ID {} - message network ID {}",
                        networkId, msgNetworkId);
                lightPeer.disconnect(ReasonCode.NULL_IDENTITY);
                ctx.pipeline().remove(lightClientHandler);
                return;
            }

            Keccak256 genesisHash = genesis.getHash();
            Keccak256 msgGenesisHash = new Keccak256(msg.getGenesisHash());
            if (!msgGenesisHash.equals(genesisHash)) {
                loggerNet.info("Removing LCHandler for {} due to unexpected genesis", ctx.channel().remoteAddress());
                loggerNet.info("Config genesis hash {} - message genesis hash {}",
                        genesisHash, msgGenesisHash);
                lightPeer.disconnect(ReasonCode.UNEXPECTED_GENESIS);
                ctx.pipeline().remove(lightClientHandler);
                return;
            }
        } catch (NoSuchElementException e) {
            loggerNet.debug("LCHandler already removed");
        }


    }

    public void sendStatusMessage(LightPeer lightPeer) {
        Block block = blockStore.getBestBlock();
        byte[] bestHash = block.getHash().getBytes();
        long bestNumber = block.getNumber();
        BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(bestHash);
        StatusMessage statusMessage = new StatusMessage(0L, (byte) 0, config.networkId(), totalDifficulty, bestHash, bestNumber, genesis.getHash().getBytes());
        lightPeer.sendMessage(statusMessage);

        loggerNet.trace("Sending status best block {} to {}",
                block.getNumber(), lightPeer.getPeerIdShort());
    }
}
