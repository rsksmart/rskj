package co.rsk.peg.feeperkb.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.AddressBasedAuthorizer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FeePerKbMainNetConstants extends FeePerKbConstants {

    private static final FeePerKbMainNetConstants instance = new FeePerKbMainNetConstants();

    public FeePerKbMainNetConstants() {
        List<ECKey> feePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "0448f51638348b034995b1fd934fe14c92afde783e69f120a46ee16eb6bdc2e4f6b5e37772094c68c0dea2b1be3d96ea9651a9eebda7304914c8047f4e3e251378",
            "0484c66f75548baf93e322574adac4e4579b6a53f8d11fab640e14c90118e6983ef24b0de349a3e88f72e81e771ae1c897cef446fd7f4da71778c532aee3b6c41b",
            "04bb6435dc1ea12da843ebe213893d136c1624acd681fff82551498ae00bf28e9323164b00daf925fa75177463b8254a2aae8a1713e4d851a84ea369c193e9ce51"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

            feePerKbChangeAuthorizer = new AddressBasedAuthorizer(
                feePerKbAuthorizedKeys,
                AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
            );

            genesisFeePerKb = Coin.MILLICOIN.multiply(5);

            maxFeePerKb = Coin.valueOf(5_000_000L);
        }

    public static FeePerKbMainNetConstants getInstance() {
        return instance;
    }
}
