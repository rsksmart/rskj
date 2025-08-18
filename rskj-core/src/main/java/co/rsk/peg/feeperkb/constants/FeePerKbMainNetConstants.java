package co.rsk.peg.feeperkb.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class FeePerKbMainNetConstants extends FeePerKbConstants {

    private static final FeePerKbMainNetConstants instance = new FeePerKbMainNetConstants();

    private FeePerKbMainNetConstants() {
        Set<RskAddress> feePerKbAuthorizedAddresses = Stream.of(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b",
            "02eec0e71e7b459f2a20db8c06a06d1132ff1bec329d3cc2d761aec570cca4fe14",
            "030b5baaac2550b527d94ea50881f4291c963cfa3638bfdec8a094cb86f6b96ed1"
        ).map(hex -> new RskAddress(ECKey.fromPublicOnly(Hex.decode(hex)).getAddress())).collect(Collectors.toSet());
        feePerKbChangeAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(feePerKbAuthorizedAddresses);

        genesisFeePerKb = Coin.valueOf(20_000L); // 20,000 satoshis per kilobyte

        maxFeePerKb = Coin.valueOf(5_000_000L);
    }

    public static FeePerKbMainNetConstants getInstance() {
        return instance;
    }
}
