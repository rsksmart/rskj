package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.PegTestUtils.*;
import static co.rsk.peg.bitcoin.BitcoinUtils.createBaseWitnessThatSpendsFromErpRedeemScript;
import static co.rsk.peg.federation.FederationTestUtils.createP2shErpFederation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.constants.*;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import java.util.*;
import java.util.stream.Stream;

import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PegUtilsGetTransactionTypeTest {
    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;
    
    private FederationContext federationContext;

    private BridgeStorageProvider provider;
    private Address userAddress;

    private List<BtcECKey> retiredFedSigners;
    private ErpFederation retiredFed;

    private List<BtcECKey> retiringFedSigners;
    private Federation retiringFederation;

    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    private int heightAtWhichToStartUsingPegoutIndex;

    @BeforeEach
    void init() {
        Context.propagate(new Context(btcMainnetParams));
        provider = mock(BridgeStorageProvider.class);
        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        retiredFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa09", "fa10", "fa00"}, true
        );
        retiredFed = createP2shErpFederation(federationMainNetConstants, retiredFedSigners);

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa06", "fa07", "fa08"}, true
        );
        retiringFederation = createP2shErpFederation(federationMainNetConstants, retiringFedSigners);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(federationMainNetConstants, activeFedSigners);

        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .build();

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        heightAtWhichToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private static Stream<Arguments> unknown_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                true,
                PegTxType.PEGIN
            ),

            Arguments.of(
                arrowheadActivations,
                false,
                false,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                true,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                false,
                true,
                PegTxType.PEGIN
            ),

            Arguments.of(
                arrowheadActivations,
                true,
                false,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                true,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                true,
                true,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                false,
                true,
                PegTxType.UNKNOWN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("unknown_args")
    void test_tx_sending_funds_to_unknown_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation,
        PegTxType expectedPegTxType
    ) {
        Script lastRetiredFederationP2SHScript = existsRetiringFederation ?
            retiringFederation.getP2SHScript() :
            retiredFed.getP2SHScript();

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        btcTransaction.addOutput(shouldSendAmountBelowMinimum? belowMinimumPeginTxValue: minimumPeginTxValue, userAddress);

        // Act
        FederationContext.FederationContextBuilder federationContextBuilder = FederationContext.builder();
        federationContextBuilder.withActiveFederation(activeFederation);

        if (existsRetiringFederation) {
            federationContextBuilder.withRetiringFederation(retiringFederation);
        }
        federationContextBuilder.withLastRetiredFederationP2SHScript(lastRetiredFederationP2SHScript);
        federationContext = federationContextBuilder.build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedPegTxType, pegTxType);
    }

    // Pegin tests
    private static Stream<Arguments> pegin_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_v1_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        int protocolVersion = 1;
        BtcECKey key = new BtcECKey();
        RskAddress rskDestinationAddress = new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress());
        Address btcRefundAddress = key.toAddress(btcMainnetParams);

        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(protocolVersion, rskDestinationAddress, Optional.of(btcRefundAddress));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void multisig_signed_pegin_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_active_fed_with_bech32_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        List<BtcECKey> unknownFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"key1", "key2", "key3"}, true
        );
        Federation unknownFed = createFederation(bridgeMainnetConstants, unknownFedSigners);

        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, Coin.COIN));
        FederationTestUtils.addSignatures(unknownFed, unknownFedSigners, btcTransaction);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_output_to_active_fed_and_other_addresses(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(BitcoinTestUtils.createHash(2), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown2"));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_multiple_outputs_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        for (int i = 0; i < 200; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(i), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.PEGIN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin_args")
    void pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedTxType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin amountPerOutput = minimumPeginTxValue.div(10);

        for (int i = 0; i < 10; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(i), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTransaction.addOutput(amountPerOutput, activeFederation.getAddress());
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedTxType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_output_to_retiring_fed_and_other_addresses(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "unknown2"));

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_retiring_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_retired_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.UNKNOWN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_retired_args")
    void pegin_retired_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, retiredFed.getAddress());

        Script lastRetiredFederationP2SHScript = retiredFed.getP2SHScript();

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withLastRetiredFederationP2SHScript(lastRetiredFederationP2SHScript)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_multiple_outputs_to_retiring_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        for (int i = 0; i < 2000; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(i), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_outputs_to_active_and_retiring_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(BitcoinTestUtils.createHash(2), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void pegin_outputs_to_active_and_retiring_fed_and_other_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(BitcoinTestUtils.createHash(2), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, userAddress);

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    // Pegins sending equal to, below and above the minimum amount

    @Test
    void pegin_below_minimum_active_fed() {
        // Arrange
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue.minus(Coin.SATOSHI), activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void anyAddressToAnyAddress_pegin_before_RSKIP379() {
        // Arrange
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0L);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(fingerrootActivations);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(minimumPeginTxValue, new Script(new byte[]{}) );

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            fingerrootActivations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            federationContext,
            anyToAnyTx,
            1
        );

        // Assert
        assertEquals(PegTxType.PEGIN, transactionType);
    }

    @Test
    void pegin_anyAddressToAnyAddress_below_minimum_pegin_before_RSIP379() {
        // Arrange
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Coin belowMinimum = bridgeMainnetConstants.getMinimumPeginTxValue(fingerrootActivations).minus(Coin.SATOSHI);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(belowMinimum, new Script(new byte[]{}));

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            fingerrootActivations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            federationContext,
            anyToAnyTx,
            1
        );

        // Assert
        assertEquals(PegTxType.PEGIN, transactionType);
    }

    @Test
    void unknown_anyAddressToAnyAddress_unknown_after_RSIP379_and_using_pegout_tx_index() {
        // Arrange
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(arrowheadActivations);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(minimumPeginTxValue, new Script(new byte[]{}) );

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            arrowheadActivations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            federationContext,
            anyToAnyTx,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.UNKNOWN, transactionType);
    }

    @Test
    void unknown_anyAddressToAnyAddress_below_minimum_unknown_after_RSIP379_and_using_pegout_tx_index() {
        // Arrange
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Coin belowMinimum = bridgeMainnetConstants.getMinimumPeginTxValue(arrowheadActivations).minus(Coin.SATOSHI);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(belowMinimum, new Script(new byte[]{}));

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            arrowheadActivations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            federationContext,
            anyToAnyTx,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.UNKNOWN, transactionType);
    }

    private static Stream<Arguments> sending_funds_below_minimum_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.PEGIN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("sending_funds_below_minimum_args")
    void sending_funds_below_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        Coin belowMinimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).div(10);

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        for (int i = 0; i < 10; i++) {
            peginTx.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());
        }

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            peginTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, transactionType);
    }

    @ParameterizedTest
    @MethodSource("sending_funds_below_minimum_args")
    void sending_funds_below_and_above_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        Coin belowMinimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).div(10);

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        for (int i = 0; i < 10; i++) {
            peginTx.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());
        }
        peginTx.addOutput(bridgeMainnetConstants.getMinimumPeginTxValue(activations), activeFederation.getAddress());
        peginTx.addOutput(Coin.COIN, activeFederation.getAddress());

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            peginTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, transactionType);
    }

    private static Stream<Arguments> sending_funds_equal_or_above_to_minimum_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("sending_funds_equal_or_above_to_minimum_args")
    void sending_funds_equal_to_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        peginTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            peginTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, transactionType);
    }

    @ParameterizedTest
    @MethodSource("sending_funds_equal_or_above_to_minimum_args")
    void sending_funds_above_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).add(Coin.CENT);

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        peginTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            peginTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGIN, transactionType);
    }

    // Pegout tests

    private static Stream<Arguments> pegout_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void pegout_no_change_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, userAddress);

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void pegout_no_change_output_sighash_no_exists_in_provider() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void many_outputs_and_inputs_pegout(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(1),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i ));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 10 ));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 20));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 30 ));
        }

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                activeFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void many_outputs_and_one_input_pegout(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i ));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 10 ));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 20));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 30 ));
        }

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                activeFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void one_outputs_and_many_input_pegout(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(1),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        btcTransaction.addOutput(minimumPegoutTxValue, userAddress);

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                activeFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void standard_pegout_sighash_no_exists_in_provider() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(activeFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, activeFederation.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    // Migration tests
    @Test
    void migration() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, inputScript);
        btcTransaction.addInput(fundTxHash, 1, inputScript);
        btcTransaction.addInput(fundTxHash, 2, inputScript);

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );
        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void migration_sighash_no_exists_in_provider() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, inputScript);
        btcTransaction.addInput(fundTxHash, 1, inputScript);
        btcTransaction.addInput(fundTxHash, 2, inputScript);

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void migration_from_retired_fed() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiredFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiredFed.getRedeemScript());

        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiredFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    private static Stream<Arguments> migration_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.PEGOUT_OR_MIGRATION
            )
        );
    }

    @ParameterizedTest
    @MethodSource("migration_args")
    void many_outputs_and_inputs_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(1),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), activeFederation.getAddress());
        }

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                retiringFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("migration_args")
    void many_outputs_and_one_input_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
        );

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), activeFederation.getAddress());
        }

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                retiringFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("migration_args")
    void one_outputs_and_many_input_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(1),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        btcTransaction.addOutput(minimumPegoutTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                retiringFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, pegTxType);
    }

    // Flyover tests
    private static Stream<Arguments> flyover_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                true,
                PegTxType.PEGIN
            ),


            Arguments.of(
                arrowheadActivations,
                false,
                false,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                true,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                false,
                true,
                PegTxType.PEGIN
            ),

            Arguments.of(
                arrowheadActivations,
                true,
                false,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                true,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                true,
                true,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                false,
                true,
                PegTxType.UNKNOWN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("flyover_args")
    void flyover_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation,
        PegTxType expectedPegTxType
    ) {
        // Arrange
        Script lastRetiredFederationP2SHScript = existsRetiringFederation ?
            retiringFederation.getP2SHScript() :
            retiredFed.getP2SHScript();

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        btcTransaction.addOutput(shouldSendAmountBelowMinimum? belowMinimumPeginTxValue: minimumPeginTxValue, flyoverFederationAddress);

        // Act
        FederationContext.FederationContextBuilder federationContextBuilder = FederationContext.builder();
        federationContextBuilder.withActiveFederation(activeFederation);
        federationContextBuilder.withLastRetiredFederationP2SHScript(lastRetiredFederationP2SHScript);
        if (existsRetiringFederation) {
            federationContextBuilder.withRetiringFederation(retiringFederation);
        }
        federationContext = federationContextBuilder.build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedPegTxType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("flyover_args")
    void flyover_segwit_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation,
        PegTxType expectedPegTxType
    ) {
        // Arrange
        Script lastRetiredFederationP2SHScript = existsRetiringFederation ?
            retiringFederation.getP2SHScript() :
            retiredFed.getP2SHScript();

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        btcTransaction.addOutput(shouldSendAmountBelowMinimum? belowMinimumPeginTxValue: minimumPeginTxValue, flyoverFederationAddress);
        btcTransaction.addOutput(createBech32Output(bridgeMainnetConstants.getBtcParams(), minimumPeginTxValue));

        // Act
        FederationContext.FederationContextBuilder federationContextBuilder = FederationContext.builder();
        federationContextBuilder.withActiveFederation(activeFederation);
        federationContextBuilder.withLastRetiredFederationP2SHScript(lastRetiredFederationP2SHScript);
        if (existsRetiringFederation) {
            federationContextBuilder.withRetiringFederation(retiringFederation);
        }
        federationContext = federationContextBuilder.build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedPegTxType, pegTxType);
    }

    private static Stream<Arguments> flyover_migration_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                false
            ),
            Arguments.of(
                arrowheadActivations,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("flyover_migration_args")
    void flyover_segwit_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction fundingTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        fundingTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        fundingTx.addOutput(Coin.COIN, flyoverFederationAddress);
        fundingTx.addOutput(createBech32Output(bridgeMainnetConstants.getBtcParams(), minimumPeginTxValue));

        BtcTransaction migrationTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        migrationTx.addInput(fundingTx.getOutput(FIRST_OUTPUT_INDEX)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        TransactionWitness txWitness = createBaseWitnessThatSpendsFromErpRedeemScript(retiringFederation.getRedeemScript());
        migrationTx.setWitness(FIRST_INPUT_INDEX, txWitness);


        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, migrationTx);

        Sha256Hash firstInputSigHash = migrationTx.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("flyover_migration_args")
    void flyover_segwit_with_other_inputs_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction fundingTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        fundingTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        fundingTx.addOutput(Coin.COIN, flyoverFederationAddress);
        fundingTx.addOutput(createBech32Output(bridgeMainnetConstants.getBtcParams(), minimumPeginTxValue));

        BtcTransaction migrationTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        migrationTx.addInput(fundingTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
        migrationTx.addOutput(Coin.COIN.multiply(10), activeFederation.getAddress());

        for (int i = 0; i < 9; i++) {
            BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
            btcTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTx.addOutput(Coin.COIN, retiringFederation.getAddress());
            migrationTx.addInput(btcTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
        }

        TransactionWitness txWitness = createBaseWitnessThatSpendsFromErpRedeemScript(retiringFederation.getRedeemScript());
        migrationTx.setWitness(FIRST_INPUT_INDEX, txWitness);
        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, migrationTx);

        Sha256Hash firstInputSigHash = migrationTx.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("flyover_migration_args")
    void flyover_segwit_with_many_inputs_and_outputs_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction fundingTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        fundingTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        fundingTx.addOutput(Coin.COIN, flyoverFederationAddress);
        fundingTx.addOutput(createBech32Output(bridgeMainnetConstants.getBtcParams(), minimumPeginTxValue));

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addInput(fundingTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        for (int i = 0; i < 9; i++) {
            BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
            btcTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTx.addOutput(Coin.COIN, retiringFederation.getAddress());
            btcTransaction.addInput(btcTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
            btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        }

        TransactionWitness txWitness = createBaseWitnessThatSpendsFromErpRedeemScript(retiringFederation.getRedeemScript());
        btcTransaction.setWitness(FIRST_INPUT_INDEX, txWitness);

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    // old fed

    private static Stream<Arguments> old_fed_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.PEGIN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("old_fed_to_live_fed_args")
    void old_fed_to_live_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
        NetworkParameters btcRegTestsParams = bridgeRegTestConstants.getBtcParams();
        Context.propagate(new Context(btcRegTestsParams));

        final List<BtcECKey> regtestOldFederationPrivateKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
            BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
            BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
        );

        Federation oldFederation = createFederation(bridgeRegTestConstants, regtestOldFederationPrivateKeys);
        assertEquals(oldFederation.getAddress().toString(), bridgeRegTestConstants.getFederationConstants().getOldFederationAddress());

        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);

        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, oldFederation.getRedeemScript())
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(oldFederation, regtestOldFederationPrivateKeys, migrationTx);

        assertTrue(PegUtilsLegacy.txIsFromOldFederation(migrationTx, oldFederation.getAddress()));

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withRetiringFederation(retiringFederation)
            .build();

        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeRegTestConstants,
            federationContext,
            migrationTx,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, transactionType);
    }

    // retired fed

    private static Stream<Arguments> retired_fed_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.PEGOUT_OR_MIGRATION
            )
        );
    }

    @ParameterizedTest
    @MethodSource("retired_fed_to_live_fed_args")
    void last_retired_fed_to_live_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        Script lastRetiredFederationP2SHScript = retiredFed.getDefaultP2SHScript();

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);

        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiredFed.getRedeemScript())
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFed, retiredFedSigners, migrationTx);

        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getSigHashForPegoutIndex(migrationTx);
        assertTrue(firstInputSigHash.isPresent());

        when(provider.hasPegoutTxSigHash(firstInputSigHash.get())).thenReturn(true);

        // Act
        federationContext = FederationContext.builder()
            .withActiveFederation(activeFederation)
            .withLastRetiredFederationP2SHScript(lastRetiredFederationP2SHScript)
            .build();

        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            migrationTx,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, transactionType);
    }

    private static Stream<Arguments> retired_fed_no_existing_in_the_storage_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                arrowheadActivations,
                true,
                PegTxType.PEGOUT_OR_MIGRATION
            )
        );
    }

    @ParameterizedTest
    @MethodSource("retired_fed_no_existing_in_the_storage_to_live_fed_args")
    void retired_fed_no_existing_in_the_storage_to_live_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        Context.propagate(new Context(btcMainnetParams));

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        Federation retiredFederation = createFederation(bridgeMainnetConstants, fedKeys);
        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);

        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiredFederation.getRedeemScript())
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFederation, fedKeys, migrationTx);

        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getSigHashForPegoutIndex(migrationTx);
        assertTrue(firstInputSigHash.isPresent());

        when(provider.hasPegoutTxSigHash(firstInputSigHash.get())).thenReturn(true);

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            federationContext,
            migrationTx,
            shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        assertEquals(expectedType, transactionType);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Pegin from user with non/parseable pub key before and after Reed, when svp is /not ongoing")
    class PeginBeforeAndAfterReed {
        private final Script parseableScriptPubKeyInputScript = ScriptBuilder.createInputScript(null, BtcECKey.fromPublicOnly(
            Hex.decode("0377a6c71c43d9fac4343f87538cd2880cf5ebefd3dd1d9aabdbbf454bca162de9")
        ));
        private final Script nonParseableScriptPubKeyInputScript = ScriptBuilder.createInputScript(null, BitcoinTestUtils.getBtcEcKeyFromSeed("abc"));

        private BridgeStorageProvider bridgeStorageProvider;
        private long rskExecutionBlockNumber;

        private void setUp(ActivationConfig.ForBlock activations, Federation retiringFed, Federation activeFed) {
            Repository repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(repository, bridgeContractAddress, btcMainnetParams, activations);

            StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
            FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            federationStorageProvider.setOldFederation(retiringFed);
            federationStorageProvider.setNewFederation(activeFed);

            FederationConstants federationConstants = bridgeMainnetConstants.getFederationConstants();
            // Move the required blocks ahead for the new powpeg to become active
            rskExecutionBlockNumber = activeFed.getCreationBlockNumber()
                + federationConstants.getFederationActivationAge(activations);

            federationContext = FederationContext.builder()
                .withActiveFederation(activeFed)
                .build();
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withParseableScriptPubKey_whenSVPOnGoing_lovellActivations_shouldThrowISE(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUp(lovellActivations, retiringFed, activeFed);

            bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

            BtcTransaction pegin = buildP2PKHPegin(parseableScriptPubKeyInputScript, activeFed);

            // act & assert
            assertThrows(
                IllegalStateException.class,
                () -> PegUtils.getTransactionType(
                    lovellActivations,
                    bridgeStorageProvider,
                    bridgeMainnetConstants,
                    federationContext,
                    pegin,
                    rskExecutionBlockNumber
                )
            );
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withParseableScriptPubKey_whenSVPOnGoing_allActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0L);
            setUp(allActivations, retiringFed, activeFed);

            bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

            BtcTransaction pegin = buildP2PKHPegin(parseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(allActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withParseableScriptPubKey_withoutSVPOnGoing_lovellActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUp(lovellActivations, retiringFed, activeFed);
            BtcTransaction pegin = buildP2PKHPegin(parseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(lovellActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withParseableScriptPubKey_withoutSVPOnGoing_allActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0L);
            setUp(allActivations, retiringFed, activeFed);
            BtcTransaction pegin = buildP2PKHPegin(parseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(allActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withNonParseableScriptPubKey_whenSVPOnGoing_lovellActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUp(lovellActivations, retiringFed, activeFed);

            bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

            BtcTransaction pegin = buildP2PKHPegin(nonParseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(lovellActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withNonParseableScriptPubKey_whenSVPOnGoing_allActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0L);
            setUp(allActivations, retiringFed, activeFed);

            bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

            BtcTransaction pegin = buildP2PKHPegin(nonParseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(allActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withNonParseableScriptPubKey_withoutSVPOnGoing_lovellActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUp(lovellActivations, retiringFed, activeFed);
            BtcTransaction pegin = buildP2PKHPegin(nonParseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(lovellActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        @ParameterizedTest
        @MethodSource("activeAndRetiringFeds")
        void getTransactionType_peginFromP2PKH_withNonParseableScriptPubKey_withoutSVPOnGoing_allActivations_shouldReturnPeginTxType(
            Federation retiringFed,
            Federation activeFed
        ) {
            // arrange
            ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0L);
            setUp(allActivations, retiringFed, activeFed);
            BtcTransaction pegin = buildP2PKHPegin(nonParseableScriptPubKeyInputScript, activeFed);

            // act
            PegTxType pegTxType =
                PegUtils.getTransactionType(allActivations, bridgeStorageProvider, bridgeMainnetConstants, federationContext, pegin, rskExecutionBlockNumber);

            // assert
            assertEquals(PegTxType.PEGIN, pegTxType);
        }

        private BtcTransaction buildP2PKHPegin(Script scriptPubKey, Federation activeFed) {
            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);

            pegin.addInput(BitcoinTestUtils.createHash(1), 0, scriptPubKey);
            pegin.addOutput(Coin.COIN, activeFed.getAddress());

            return pegin;
        }

        private static Stream<Arguments> activeAndRetiringFeds() {
            List<BtcECKey> realActiveFedKeys = List.of(
                BtcECKey.fromPublicOnly(Hex.decode("02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209")),
                BtcECKey.fromPublicOnly(Hex.decode("0222caa9b1436ebf8cdf0c97233a8ca6713ed37b5105bcbbc674fd91353f43d9f7")),
                BtcECKey.fromPublicOnly(Hex.decode("022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c")),
                BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")),
                BtcECKey.fromPublicOnly(Hex.decode("02b1645d3f0cff938e3b3382b93d2d5c082880b86cbb70b6600f5276f235c28392")),
                BtcECKey.fromPublicOnly(Hex.decode("030297f45c6041e322ecaee62eb633e84825da984009c731cba980286f532b8d96")),
                BtcECKey.fromPublicOnly(Hex.decode("039ee63f1e22ed0eb772fe0a03f6c34820ce8542f10e148bc3315078996cb81b25")),
                BtcECKey.fromPublicOnly(Hex.decode("03e2fbfd55959660c94169320ed0a778507f8e4c7a248a71c6599a4ce8a3d956ac")),
                BtcECKey.fromPublicOnly(Hex.decode("03eae17ad1d0094a5bf33c037e722eaf3056d96851450fb7f514a9ed3af1dbb570"))
            );

            Federation retiringFederationLegacy = P2shErpFederationBuilder.builder()
                .build();
            Federation retiringFederationSegwit = P2shP2wshErpFederationBuilder.builder()
                .build();

            int activeFedCreationBlockNumber = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates()
                + bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
            Federation activeFederationLegacy = P2shErpFederationBuilder.builder()
                .withCreationBlockNumber(activeFedCreationBlockNumber)
                .withMembersBtcPublicKeys(realActiveFedKeys)
                .build();
            Federation activeFederationSegwit = P2shP2wshErpFederationBuilder.builder()
                .withCreationBlockNumber(activeFedCreationBlockNumber)
                .withMembersBtcPublicKeys(realActiveFedKeys)
                .build();

            return Stream.of(
                Arguments.of(retiringFederationLegacy, activeFederationLegacy),
                Arguments.of(retiringFederationLegacy, activeFederationSegwit),
                Arguments.of(retiringFederationSegwit, activeFederationSegwit)
            );
        }
    }
}
