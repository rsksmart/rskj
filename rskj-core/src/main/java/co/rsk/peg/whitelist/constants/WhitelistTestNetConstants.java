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
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b"
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
