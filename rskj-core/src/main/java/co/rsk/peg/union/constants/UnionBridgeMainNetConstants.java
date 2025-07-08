package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.ethereum.crypto.ECKey;

public class UnionBridgeMainNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeMainNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        // TODO: Replace with actual address
        unionBridgeAddress = new RskAddress("0000000000000000000000000000000000000000");

        // TODO: Replace with actual initial locking cap value and increments multiplier
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(300)); // 300 rbtc
        lockingCapIncrementsMultiplier = 2;

        // TODO: Replace with actual authorizers
        List<ECKey> changeUnionBridgeContractAddressAuthorizers = Collections.singletonList(
            ECKey.fromPrivate(BigInteger.ZERO)
        );

        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            changeUnionBridgeContractAddressAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeLockingCapAuthorizers = Collections.singletonList(
            ECKey.fromPrivate(BigInteger.ZERO)
        );
        changeLockingCapAuthorizer = new AddressBasedAuthorizer(
            changeLockingCapAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeTransferPermissionsAuthorizers = Collections.singletonList(
            ECKey.fromPrivate(BigInteger.ZERO)
        );
        changeTransferPermissionsAuthorizer = new AddressBasedAuthorizer(
            changeTransferPermissionsAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
