package co.rsk;

import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RskTestUtils {
    public static List<ECKey> getEcKeysFromSeeds(String[] seeds) {
        return Arrays.stream(seeds)
            .map(seed -> seed.getBytes(StandardCharsets.UTF_8))
            .map(HashUtil::keccak256)
            .map(ECKey::fromPrivate)
            .collect(Collectors.toList());
    }

    public static List<ECKey> getRskPublicKeysFromFederation(Federation federation) {
        List<ECKey> rskPublicKeys = new ArrayList<>();
        List<FederationMember> members = federation.getMembers();
        for (FederationMember member : members) {
            rskPublicKeys.add(member.getRskPublicKey());
        }

        return rskPublicKeys;
    }
}
