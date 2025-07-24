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
            "0400fea26eef55e5df7ebe2ce3ba8f6c7f67d88f64946d8c6a66f07797a7cb8bd65c09ffd578393dd355968436d127c3f9264c7ebc396d257a3852f586b84aa2c5",
            "048148da8c89f9b04452e2853487ac933913a0e868aa05585ae60ddc26b7cb3f7c04c57fcdb3233c217f9220e6dbc3c394aa0d79b5a599af60ee4d9e17d906c137",
            "04752c732b98578cc933968ce47b92b3b63f60effee25cbaf772e7bd707e0a2d9428737a0e59f34d86b6d737b3af5375011d153c68de2c140e026b0378107789fc"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList()));

        increaseAuthorizer = new AddressBasedAuthorizer(
            increaseAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        initialValue = Coin.COIN.multiply(200L); // 200 BTC
        incrementsMultiplier = 2;
    }

    public static LockingCapTestNetConstants getInstance() {
        return instance;
    }
}
