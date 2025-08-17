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
            "043fa8cdeaf582bad2afc80a3d4848e7224c6fe8f57f7392fc18dc33ef13d5ccf12fa0c81690075ad9b5b3f90b4129de1a6130816f5c54b4b3b5708f9186b460fa"
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
