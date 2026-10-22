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
            new RskAddress("151455507b211303224787883daf5634a7220edc")
        );

        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("3f59c79f9552fc4bd66871615ac9f0b3987bdd34")
        );

        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("06753cff26938034ce58863d8b4a30f4f6a92d58")
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
