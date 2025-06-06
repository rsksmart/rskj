package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class UnionBridgeRegTestConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeRegTestConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeRegTestConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        // TODO: Replace with actual address
        unionBridgeAddress = new RskAddress("5988645d30cd01e4b3bc2c02cb3909dec991ae31");

        // TODO: Replace with actual initial value and increments multiplier
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(500)); // 500 rbtc
        lockingCapIncrementsMultiplier = 4;

        // TODO: Replace with actual authorizers
        List<ECKey> authorizers = Collections.singletonList(
            // seed: unionBridgeAuthorizer
            ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
        );
        changeAuthorizer = new AddressBasedAuthorizer(
            authorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeLockingCapAuthorizers = Collections.singletonList(
            // seed: changeLockingCapAuthorizer
            ECKey.fromPublicOnly(Hex.decode("049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b"))
        );
        changeLockingCapAuthorizer = new AddressBasedAuthorizer(
            changeLockingCapAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
