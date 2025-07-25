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

        // Same as testnet
        ECKey authorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04bf7e3bca7f7c58326382ed9c2516a8773c21f1b806984bb1c5c33bd18046502d97b28c0ea5b16433fbb2b23f14e95b36209f304841e814017f1ede1ecbdcfce3"
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
