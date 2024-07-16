package co.rsk.peg.lockingcap.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class LockingCapRegTestConstants extends LockingCapConstants {

    private static final LockingCapRegTestConstants instance = new LockingCapRegTestConstants();

    private LockingCapRegTestConstants() {
        ECKey authorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04450bbaab83ec48b3cb8fbb077c950ee079733041c039a8c4f1539e5181ca1a27589eeaf0fbf430e49d2909f14c767bf6909ad6845831f683416ee12b832e36ed"
        ));

        List<ECKey> increaseAuthorizedKeys = Collections.singletonList(authorizerPublicKey);

        increaseAuthorizer = new AddressBasedAuthorizer(
            increaseAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        initialValue = Coin.COIN.multiply(1_000L); // 1000 BTC
        incrementsMultiplier = 2;
    }

    public static LockingCapRegTestConstants getInstance() {
        return instance;
    }
}
