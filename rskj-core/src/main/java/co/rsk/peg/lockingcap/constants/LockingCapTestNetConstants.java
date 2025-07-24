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
            "04339027256892db5d03bd3835fde93551941b3c5b9ad765b8e8d3451e3b7a2b3ed7c795665d7c20da2416f4be67e23b19a7654c29ce983acf5936c1705d105276",
            "043267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e75b8577faac2183782cb0d76820cf9f356831d216e99d886f8a6bc47fe696939",
            "0455db9b3867c14e84a6f58bd2165f13bfdba0703cb84ea85788373a6a109f3717e40483aa1f8ef947f435ccdf10e530dd8b3025aa2d4a7014f12180ee3a301d27"
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
