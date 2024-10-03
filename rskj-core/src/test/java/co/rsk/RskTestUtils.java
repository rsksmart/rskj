package co.rsk;

import static org.mockito.Mockito.mock;

import co.rsk.crypto.Keccak256;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

public class RskTestUtils {

    public static Keccak256 createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (nHash & 0xFF);
        bytes[1] = (byte) (nHash >>8 & 0xFF);

        return new Keccak256(bytes);
    }

    public static ECKey getEcKeyFromSeed(String seed) {
        byte[] seedHash = HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8));
        return ECKey.fromPrivate(seedHash);
    }

    public static List<ECKey> getEcKeysFromSeeds(String[] seeds) {
        return Arrays.stream(seeds)
            .map(RskTestUtils::getEcKeyFromSeed)
            .toList();
    }

    public static Block createRskBlock() {
        final int defaultBlockNumber = 1001;
        final Instant defaultBlockTimestamp = ZonedDateTime.parse("2020-01-20T12:00:08.400Z").toInstant();

        return createRskBlock(defaultBlockNumber, defaultBlockTimestamp.toEpochMilli());
    }

    public static Block createRskBlock(long blockNumber, long blockTimestamp) {
        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .setTimestamp(blockTimestamp)
            .build();

        return Block.createBlockFromHeader(blockHeader, true);
    }
}
