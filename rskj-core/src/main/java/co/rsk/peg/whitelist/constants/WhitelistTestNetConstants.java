package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistTestNetConstants extends WhitelistConstants {

    private static final WhitelistTestNetConstants instance = new WhitelistTestNetConstants();

    private WhitelistTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        ECKey authorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04339027256892db5d03bd3835fde93551941b3c5b9ad765b8e8d3451e3b7a2b3ed7c795665d7c20da2416f4be67e23b19a7654c29ce983acf5936c1705d105276"
        ));
        List<ECKey> lockWhitelistAuthorizedKeys = Collections.singletonList(authorizerPublicKey);

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static WhitelistTestNetConstants getInstance() {
        return instance;
    }
}
