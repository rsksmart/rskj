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

public class UnionBridgeMainNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeMainNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        // TODO: Replace with actual address
        // seed: UNION_BRIDGE_ADDRESS
        unionBridgeAddress = new RskAddress("5988645d30cd01e4b3bc2c02cb3909dec991ae31");

        // TODO: Replace with actual initial locking cap value and increments multiplier
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(300)); // 300 rbtc
        lockingCapIncrementsMultiplier = 2;

        // TODO: Replace with actual authorizers
        List<ECKey> changeUnionBridgeContractAddressAuthorizers = Collections.singletonList(
            // seed: unionBridgeAuthorizer
            ECKey.fromPublicOnly(Hex.decode("041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
        );

        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            changeUnionBridgeContractAddressAuthorizers,
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

        // TODO: Replace with actual authorizers
        List<ECKey> changeTransferPermissionsAuthorizers = Collections.singletonList(
            // seed: changeTransferPermissionsAuthorizer
            ECKey.fromPublicOnly(Hex.decode("04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f"))
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
