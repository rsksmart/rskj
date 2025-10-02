package co.rsk.peg.union.constants;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.math.BigInteger;

public class UnionBridgeMainNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeMainNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        // TODO: Replace with actual address
        unionBridgeAddress = ZERO_ADDRESS;

        // TODO: Replace with actual initial locking cap value and increments multiplier
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(300)); // 300 rbtc
        lockingCapIncrementsMultiplier = 2;

        changeUnionBridgeContractAddressAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(ZERO_ADDRESS);

        // TODO: Replace with actual authorizers
        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            ZERO_ADDRESS
        );

        // TODO: Replace with actual authorizers
        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            ZERO_ADDRESS
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
