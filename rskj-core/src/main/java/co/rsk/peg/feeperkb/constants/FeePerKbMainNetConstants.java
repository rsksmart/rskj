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
            "02f94dade3b1fbb7d066e6280852f33d29917a8edc4306b4a5d77c0eeb1566afa2",
            "02c6732476e084a1d8788c2aa5f3aeb7966ce295d70e751efdab5a17a6b4d597d5",
            "02c10071384c7766f6db4a71cb532e16177629401c27ae725c4fc7105a94a7907b"
        ).map(hex -> new RskAddress(ECKey.fromPublicOnly(Hex.decode(hex)).getAddress())).collect(Collectors.toSet());
        feePerKbChangeAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(feePerKbAuthorizedAddresses);

        genesisFeePerKb = Coin.valueOf(20_000L); // 20,000 satoshis per kilobyte

        maxFeePerKb = Coin.valueOf(5_000_000L);
    }

    public static FeePerKbMainNetConstants getInstance() {
        return instance;
    }
}
