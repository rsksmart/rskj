package co.rsk;

import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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
}
