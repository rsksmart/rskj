package co.rsk;

import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RskTestUtils {

    public static Keccak256 createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (nHash & 0xFF);
        bytes[1] = (byte) (nHash >>8 & 0xFF);

        return new Keccak256(bytes);
    }

    public static List<ECKey> getEcKeysFromSeeds(String[] seeds) {
        return Arrays.stream(seeds)
            .map(seed -> seed.getBytes(StandardCharsets.UTF_8))
            .map(HashUtil::keccak256)
            .map(ECKey::fromPrivate)
            .collect(Collectors.toList());
    }
}
