package co.rsk.peg.union.constants;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.math.BigInteger;

public class UnionBridgeTestNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeTestNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        unionBridgeAddress = ZERO_ADDRESS;

        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc);
        lockingCapIncrementsMultiplier = 2;

        changeUnionBridgeContractAddressAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("c38c7f0bcdf679dd360dee652d83be7d5b386956")
        );

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
