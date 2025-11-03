package co.rsk.peg.feeperkb.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FeePerKbTestNetConstants extends FeePerKbConstants {

    private static final FeePerKbTestNetConstants instance = new FeePerKbTestNetConstants();

    private FeePerKbTestNetConstants() {
        List<ECKey> feePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b",
            "02eec0e71e7b459f2a20db8c06a06d1132ff1bec329d3cc2d761aec570cca4fe14",
            "030b5baaac2550b527d94ea50881f4291c963cfa3638bfdec8a094cb86f6b96ed1"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            feePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        genesisFeePerKb = Coin.valueOf(20_000L); // 20,000 satoshis per kilobyte

        maxFeePerKb = Coin.valueOf(5_000_000L);
    }

    public static FeePerKbTestNetConstants getInstance() {
        return instance;
    }
}
