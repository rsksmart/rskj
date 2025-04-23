package co.rsk.peg.union;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeSupportImplTest {

    private static final ActivationConfig.ForBlock lovell700 = ActivationConfigsForTest.lovell700().forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private static final RskAddress regtestAuthorizerRskAddress = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());
    private static final RskAddress testnetAuthorizerRskAddress = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());

    private static final RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final RskAddress secondNewUnionBridgeContractAddress = TestUtils.generateAddress(
        "secondNewUnionBridgeContractAddress");
    private StorageAccessor storageAccessor;
    private UnionBridgeStorageProvider unionBridgeStorageProvider;
    private SignatureCache signatureCache;
    private Transaction tx;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        signatureCache = mock(SignatureCache.class);
        tx = mock(Transaction.class);
    }

    private static Stream<Arguments> unionBridgeConstantsAndAuthorizerProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), regtestAuthorizerRskAddress),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), testnetAuthorizerRskAddress)
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_beforeRSKIP502_whenMeetRequirementsToUpdate_shouldReturnSuccessCodeButNotStoreAnyAddress(
        UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            lovell700,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            newUnionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS.getCode(),
            result
        );
        assertAddressWasSet(newUnionBridgeContractAddress);
        assertAddressWasNotStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_postRSKIP502_whenMeetRequirementsToUpdate_shouldReturnSuccessCode(
        UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            newUnionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS.getCode(),
            result
        );
        assertAddressWasSet(newUnionBridgeContractAddress);
    }

    private void assertAddressWasSet(RskAddress expectedAddress) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        Assertions.assertTrue(actualAddress.isPresent());
        Assertions.assertEquals(expectedAddress, actualAddress.get());
    }

    private void assertAddressWasStored(RskAddress expectedAddress) {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertEquals(expectedAddress, actualRskAddress);
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_preRSKIP502_whenAddressIsTheInitialAddressInConstants_shouldReturnGenericErrorCode(
        UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            lovell700,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            unionBridgeConstants.getAddress());

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE.getCode(),
            result
        );
        assertAddressWasNotSet(lovell700);
        assertAddressWasNotStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_postRSKIP502_whenAddressIsTheInitialAddressInConstants_shouldReturnGenericErrorCode(
        UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            unionBridgeConstants.getAddress());

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE.getCode(),
            result
        );
        assertAddressWasNotSet(allActivations);
        assertAddressWasNotStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_whenAddressAlreadyExistsInStorage_shouldReturnSuccessCode(
        UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);
        // Save the address in storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            newUnionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // act & assert
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx, secondNewUnionBridgeContractAddress);
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS.getCode(),
            result
        );
        assertAddressWasSet(secondNewUnionBridgeContractAddress);

        // Check that the address was stored in the storage
        unionBridgeSupport.save();
        assertAddressWasStored(secondNewUnionBridgeContractAddress);
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenMainnet_shouldReturnEnvironmentDisabledCode() {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            UnionBridgeMainNetConstants.getInstance(),
            unionBridgeStorageProvider,
            signatureCache
        );

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            newUnionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.ENVIRONMENT_DISABLED.getCode(),
            result
        );
        assertAddressWasNotSet(allActivations);
        assertAddressWasNotStored();
    }

    private void assertAddressWasNotSet(ActivationConfig.ForBlock activations) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(activations);
        Assertions.assertTrue(actualAddress.isEmpty());
    }

    private void assertAddressWasNotStored() {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertNull(actualRskAddress);
    }

    private static Stream<Arguments> unionBridgeConstants() {
        return Stream.of(
            Arguments.of(UnionBridgeTestNetConstants.getInstance()),
            Arguments.of(UnionBridgeTestNetConstants.getInstance())
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstants")
    void setUnionBridgeContractAddressForTestnet_whenCallerIsNotAuthorized_shouldReturnUnauthorizedCode(UnionBridgeConstants unionBridgeConstants) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(TestUtils.generateAddress("notAuthorizedAddress"));

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            newUnionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.UNAUTHORIZED_CALLER.getCode(),
            result
        );
        assertAddressWasNotSet(allActivations);
        assertAddressWasNotStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsNull_shouldReturnUnauthorizedCode(UnionBridgeConstants unionBridgeConstants, RskAddress authorizerAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerAddress);

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            null);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE.getCode(),
            result
        );
        assertAddressWasNotSet(allActivations);
        assertAddressWasNotStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsEmpty_shouldReturnUnauthorizedCode(UnionBridgeConstants unionBridgeConstants, RskAddress authorizerAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerAddress);

        // act
        RskAddress emptyAddress = new RskAddress(new byte[20]);
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            emptyAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE.getCode(),
            result
        );
        assertAddressWasNotSet(allActivations);
        assertAddressWasNotStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void save_beforeRSKIP502_shouldNotSave(UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            lovell700,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);
        unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx, newUnionBridgeContractAddress);

        // act
        unionBridgeSupport.save();

        // assert
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );

        Assertions.assertNull(actualRskAddress);
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsAndAuthorizerProvider")
    void save_afterRSKIP502_shouldSave(UnionBridgeConstants unionBridgeConstants, RskAddress authorizerRskAddress) {
        // arrange
        UnionBridgeSupport unionBridgeSupport = new UnionBridgeSupportImpl(
            allActivations,
            unionBridgeConstants,
            unionBridgeStorageProvider,
            signatureCache
        );
        when(tx.getSender(signatureCache)).thenReturn(authorizerRskAddress);
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(tx,
            newUnionBridgeContractAddress);
        Assertions.assertEquals(actualResponseCode, UnionResponseCode.SUCCESS.getCode());

        // act
        unionBridgeSupport.save();

        // assert
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertEquals(newUnionBridgeContractAddress, actualRskAddress);
    }
}
