package co.rsk;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RskTestUtils {
    public static List<ECKey> getEcKeysFromSeeds(String[] seeds) {
        return Arrays
            .stream(seeds)
            .map(seed -> ECKey.fromPrivate(HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8))))
            .collect(Collectors.toList());
    }
}
