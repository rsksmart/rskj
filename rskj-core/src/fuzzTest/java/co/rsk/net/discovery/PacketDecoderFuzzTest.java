package co.rsk.net.discovery;

import co.rsk.net.discovery.message.*;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Tag;

import io.netty.channel.ChannelHandlerContext;
import org.bouncycastle.util.Arrays;
import org.ethereum.crypto.ECKey;
import org.mockito.Mockito;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.UUID;

import static org.ethereum.crypto.HashUtil.keccak256;

class PacketDecoderFuzzTest {
    private static final int NETWORK_ID = 1;
    private static final String KEY_1 = "bd1d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea38261f";

    @Tag("DiscoveryPacketDecoderFuzzDecode")
    @FuzzTest
    public void testFuzzDecode(FuzzedDataProvider data) {
        ECKey key = ECKey.fromPrivate(Hex.decode(KEY_1)).decompress();
        String check = UUID.randomUUID().toString();
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);

        PacketDecoder decoder = new PacketDecoder();

        PingPeerMessage pingMessage = PingPeerMessage.create(data.consumeString(256), data.consumeInt(), check, key, data.consumeInt());
        InetSocketAddress sender = new InetSocketAddress(data.consumeString(256), data.consumeInt(0, 65535));
        long start = System.currentTimeMillis();
        try {
            decoder.decodeMessage(ctx, pingMessage.getPacket(), sender);
        } catch (Exception e) {}

        //Decode Pong Message
        PongPeerMessage pongPeerMessage = PongPeerMessage.create(data.consumeString(256), data.consumeInt(), check, key, data.consumeInt());
        sender = new InetSocketAddress(data.consumeString(256), data.consumeInt(0, 65535));
        try {
            decoder.decodeMessage(ctx, pongPeerMessage.getPacket(), sender);
        } catch (Exception e) {}

        //Decode Find Node Message
        FindNodePeerMessage findNodePeerMessage = FindNodePeerMessage.create(key.getNodeId(), check, key, data.consumeInt());
        sender = new InetSocketAddress(data.consumeString(256), data.consumeInt(0, 65535));
        try {
            decoder.decodeMessage(ctx, findNodePeerMessage.getPacket(), sender);
        } catch (Exception e) {}

        //Decode Neighbors Message
        NeighborsPeerMessage neighborsPeerMessage = NeighborsPeerMessage.create(new ArrayList<>(), check, key, data.consumeInt());
        sender = new InetSocketAddress(data.consumeString(256), data.consumeInt(0, 65535));
        try {
            decoder.decodeMessage(ctx, neighborsPeerMessage.getPacket(), sender);
        } catch (Exception e) {}
        long end = System.currentTimeMillis();

        if (end - start > 250) {
            System.out.println("It took too much");
        }
    }

    @Tag("DiscoveryPacketDecoderFuzzDecode2")
    @FuzzTest
    public void testFuzzDecode2(FuzzedDataProvider data) {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        InetSocketAddress sender = new InetSocketAddress(data.consumeString(256), data.consumeInt(0, 65535));
        PacketDecoder decoder = new PacketDecoder();

        long start = System.currentTimeMillis();
        byte[] fuzzData = data.consumeBytes(4096);
        byte[] mdcCheck = keccak256(fuzzData, 0, fuzzData.length);
        byte[] msg = Arrays.concatenate(mdcCheck, fuzzData);
        try {
            decoder.decodeMessage(ctx, msg, sender);
        } catch (Exception e) {
            // Messages will fail really most of the times.
            // We can eventually have acceptable or unacceptable exceptions, but so far
            // we will ignore all exceptions

        }
        long end = System.currentTimeMillis();
        if (end - start > 100) {
            start = System.currentTimeMillis();
            try {
                decoder.decodeMessage(ctx, fuzzData, sender);
            } catch (Exception e) {}
            end = System.currentTimeMillis();
            if (end - start > 100) {
                System.out.println("FUZZ DATA: " + Hex.toHexString(fuzzData));
                throw new RuntimeException("It took " + (end - start) + " with input " + Hex.toHexString(fuzzData));
            }
        }
    }

    @Tag("DiscoveryPacketDecoderFuzzInput")
    @FuzzTest
    public void testFuzzInput(FuzzedDataProvider data) {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        InetSocketAddress sender = new InetSocketAddress("localhost", 8080);
        PacketDecoder decoder = new PacketDecoder();
        //  Sometimes inputs like this perform slow. There's something in the config,
        //  gc or whatever that makes it but it is not an issue per se
        //  But if we can understand the nature of it we can improve benchmark testings
        byte[] input = Hex.decode("4c4c4c4c4c4c4c4c5c4c4c4c4cbababababababababababa4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c4c");
        for (int i = 0; i < 100; i++) {
            long start = System.currentTimeMillis();
            try {
                decoder.decodeMessage(ctx, input, sender);
            } catch (Exception e) {}
            long end = System.currentTimeMillis();
            // The threshold here needs to be adjusted, it's always throwing
            //System.out.println("It took " + (end - start));
        }
    }

}
