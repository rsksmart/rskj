package co.rsk.peg.lockingcap.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class LockingCapTestNetConstants extends LockingCapConstants {

    private static final LockingCapTestNetConstants instance = new LockingCapTestNetConstants();

    private LockingCapTestNetConstants() {
        List<ECKey> increaseAuthorizedKeys = Collections.unmodifiableList(Stream.of(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b",
            "02eec0e71e7b459f2a20db8c06a06d1132ff1bec329d3cc2d761aec570cca4fe14",
            "030b5baaac2550b527d94ea50881f4291c963cfa3638bfdec8a094cb86f6b96ed1"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList()));

        increaseAuthorizer = new AddressBasedAuthorizer(
            increaseAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        initialValue = Coin.COIN.multiply(21_000_000L); // 21 million BTC
        incrementsMultiplier = 2;
    }

    public static LockingCapTestNetConstants getInstance() {
        return instance;
    }
}
