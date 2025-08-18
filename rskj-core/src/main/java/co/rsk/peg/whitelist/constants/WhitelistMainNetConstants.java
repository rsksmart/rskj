package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistMainNetConstants extends WhitelistConstants {

    private static final WhitelistMainNetConstants instance = new WhitelistMainNetConstants();

    private WhitelistMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        ECKey authorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b"
        ));
        List<ECKey> lockWhitelistAuthorizedKeys = Collections.singletonList(authorizerPublicKey);

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static WhitelistMainNetConstants getInstance() {
        return instance;
    }
}
