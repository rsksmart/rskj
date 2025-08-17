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
            "0454c8532676f580543750f8e47ce63f74a1e7086eff28e6c6742c404c62ee6a0aff059951c74360d820afef03dda28d1ff155c7e6bc0f82ac37adfa4c4794a8ad",
            "049be6581113de22e566d3089ca4d8d2e6f94ae7f60c18772e00580a2c8fe850d62079395f8d8eb36bb341355a457a6481a8f469f7ed46ef8f5609863342b382a2",
            "04692ac30246337d142be8baf667d3cdafd3506f35e1b432284ef60b73bf56757dec3500e0b920e17fac04ae58c7b4e6392aaebf1b1bbfbbb65c15daafd78cebb1"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            feePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        genesisFeePerKb = Coin.MILLICOIN;

        maxFeePerKb = Coin.valueOf(5_000_000L);
    }

    public static FeePerKbTestNetConstants getInstance() {
        return instance;
    }
}
