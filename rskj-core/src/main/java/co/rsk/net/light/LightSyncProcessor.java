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
            LightStatus status = msg.getStatus();
            loggerNet.debug("Receiving Status - block {} {}", status.getBestNumber(), HashUtil.shortHash(status.getBestHash()));

            byte protocolVersion = status.getProtocolVersion();
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
            int msgNetworkId = status.getNetworkId();
            if (msgNetworkId != networkId) {
                loggerNet.info("Removing LCHandler for {} due to invalid network", ctx.channel().remoteAddress());
                loggerNet.info("Different network received: config network ID {} - message network ID {}",
                        networkId, msgNetworkId);
                lightPeer.disconnect(ReasonCode.NULL_IDENTITY);
                ctx.pipeline().remove(lightClientHandler);
                return;
            }

            Keccak256 genesisHash = genesis.getHash();
            Keccak256 msgGenesisHash = new Keccak256(status.getGenesisHash());
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
        LightStatus status = getCurrentStatus(block);
        StatusMessage statusMessage = new StatusMessage(0L, status);

        lightPeer.sendMessage(statusMessage);

        loggerNet.trace("Sending status best block {} to {}",
                block.getNumber(), lightPeer.getPeerIdShort());
    }

    private LightStatus getCurrentStatus(Block block) {
        byte[] bestHash = block.getHash().getBytes();
        long bestNumber = block.getNumber();
        BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(bestHash);
        return new LightStatus((byte) 0, config.networkId(), totalDifficulty, bestHash, bestNumber, genesis.getHash().getBytes());
    }
}
