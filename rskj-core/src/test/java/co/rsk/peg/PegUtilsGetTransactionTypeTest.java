package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PegUtilsGetTransactionTypeTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    private BridgeStorageProvider provider;
    private Address userAddress;

    private Federation retiredFed;
    private List<BtcECKey> retiringFedSigners;
    private Federation retiringFederation;
    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    protected int blockNumberToStartUsingPegoutIndex;

    @BeforeEach
    void init() {
        Context.propagate(new Context(btcMainnetParams));
        provider = mock(BridgeStorageProvider.class);
        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        retiredFed = createP2shErpFederation(bridgeMainnetConstants, BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa09", "fa10", "fa11"}, true
        ));

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa06", "fa07", "fa08"}, true
        );
        retiringFederation = createP2shErpFederation(bridgeMainnetConstants, retiringFedSigners);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;
        blockNumberToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private static Stream<Arguments> unknown_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
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
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                true,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),


            Arguments.of(
                tbdActivations,
                false,
                false,
                false,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                true,
                false,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                true,
                true,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),

            Arguments.of(
                tbdActivations,
                true,
                false,
                false,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                false,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                true,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                false,
                true,
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
        Boolean belowTheMinimumPegInValue,
        Boolean existsRetiringFederation,
        Boolean existsRetiredFederation,
        PegTxType expectedPegTxType
    ) {
        // Arrange
        if (existsRetiredFederation){
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiredFed.getP2SHScript()));
        }

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        btcTransaction.addOutput(belowTheMinimumPegInValue? belowMinimumPeginTxValue: minimumPeginTxValue, userAddress);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            existsRetiringFederation? retiringFederation: null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedPegTxType, pegTxType);
    }

    // Pegin tests
    @Test
    void test_pegin_below_minimum_active_fed() {
        // Arrange
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue.minus(Coin.SATOSHI), activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false
            ),
            Arguments.of(
                tbdActivations,
                false
            ),
            Arguments.of(
                tbdActivations,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_multisig_pegin_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Address multisigAddress = BitcoinTestUtils.createP2SHMultisigAddress(
            btcMainnetParams,
            BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"key1", "key2", "key3"}, true
        ));

        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createOutputScript(multisigAddress)
        );
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_active_fed_with_bech32_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Address multisigAddress = BitcoinTestUtils.createP2SHMultisigAddress(
            btcMainnetParams,
            BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"key1", "key2", "key3"}, true
            ));

        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createOutputScript(multisigAddress)
        );
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, Coin.COIN));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_active_fed(
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_output_to_active_fed_and_other_addresses(
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_multiple_outputs_to_active_fed(
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                PegTxType.PEGIN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin_args")
    void test_pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin(
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedTxType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_output_to_retiring_fed_and_other_addresses(
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_retiring_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, retiringFederation.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_retired_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                true,
                PegTxType.UNKNOWN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_retired_args")
    void test_pegin_retired_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiredFed.getP2SHScript()));

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, retiredFed.getAddress());

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_multiple_outputs_to_retiring_fed(
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_outputs_to_active_and_retiring_fed(
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegin_args")
    void test_pegin_outputs_to_active_and_retiring_fed_and_other_address(
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    // Pegins sending equal to, below and above the minimum amount
    @Test
    void anyAddressToAnyAddress_pegin_before_RSIP379() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(minimumPeginTxValue, new Script(new byte[]{}) );

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            activeFederation,
            null,
            anyToAnyTx,
            1
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    @Test
    void pegin_anyAddressToAnyAddress_below_minimum_pegin_before_RSIP379() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Coin belowMinimum = bridgeMainnetConstants.getMinimumPeginTxValue(activations).minus(Coin.SATOSHI);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(belowMinimum, new Script(new byte[]{}));

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            activeFederation,
            null,
            anyToAnyTx,
            1
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    @Test
    void unknown_anyAddressToAnyAddress_unknown_after_RSIP379_and_using_pegout_tx_index() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(minimumPeginTxValue, new Script(new byte[]{}) );

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;;
        int blockNumberToStartUsingNewGeTransactionTypeMechanism = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            activeFederation,
            null,
            anyToAnyTx,
            blockNumberToStartUsingNewGeTransactionTypeMechanism
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }

    @Test
    void unknown_anyAddressToAnyAddress_below_minimum_unknown_after_RSIP379_and_using_pegout_tx_index() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);

        Coin belowMinimum = bridgeMainnetConstants.getMinimumPeginTxValue(activations).minus(Coin.SATOSHI);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(belowMinimum, new Script(new byte[]{}));

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;;
        int blockNumberToStartUsingNewGeTransactionTypeMechanism = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            activeFederation,
            null,
            anyToAnyTx,
            blockNumberToStartUsingNewGeTransactionTypeMechanism
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }

    private static Stream<Arguments> sending_funds_below_minimum_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
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
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Coin belowMinimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).div(10);

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

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
            activeFederation,
            null,
            peginTx,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, transactionType);
    }

    @ParameterizedTest
    @MethodSource("sending_funds_below_minimum_args")
    void sending_funds_below_and_above_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Coin belowMinimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).div(10);

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

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
            activeFederation,
            null,
            peginTx,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, transactionType);
    }

    private static Stream<Arguments> sending_funds_equal_or_above_to_minimum_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                true,
                PegTxType.PEGIN
            )
        );
    }

    @ParameterizedTest
    @MethodSource("sending_funds_equal_or_above_to_minimum_args")
    void sending_funds_equal_to_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        peginTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            peginTx,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, transactionType);
    }

    @ParameterizedTest
    @MethodSource("sending_funds_equal_or_above_to_minimum_args")
    void sending_funds_above_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).add(Coin.CENT);

        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        BtcTransaction peginTx = new BtcTransaction(btcMainnetParams);
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        peginTx.addOutput(minimumPeginTxValue, activeFederation.getAddress());

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            peginTx,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, transactionType);
    }

    // Pegout tests

    private static Stream<Arguments> pegout_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
                true,
                PegTxType.PEGOUT_OR_MIGRATION
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void test_pegout_no_change_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, activeFederation.getRedeemScript());
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @Test
    void test_pegout_no_change_output_sighash_no_exists_in_provider() {
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
            activeFederation,
            null,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void test_many_outputs_and_inputs_pegout(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, activeFederation.getRedeemScript());
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValueInSatoshis();
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void test_many_outputs_and_one_input_pegout(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, activeFederation.getRedeemScript());

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValueInSatoshis();
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("pegout_args")
    void test_one_outputs_and_many_input_pegout(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, activeFederation.getRedeemScript());
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValueInSatoshis();
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @Test
    void test_standard_pegout_sighash_no_exists_in_provider() {
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
            activeFederation,
            null,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    // Migration tests
    @Test
    void test_migration() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 1, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 2, new Script(new byte[]{}));

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );
        when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void test_migration_sighash_no_exists_in_provider() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 1, new Script(new byte[]{}));
        btcTransaction.addInput(fundTxHash, 2, new Script(new byte[]{}));

        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void test_migration_from_retired_fed() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, retiredFed.getP2SHScript());
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
            activeFederation,
            null,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    private static Stream<Arguments> migration_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
                true,
                PegTxType.PEGOUT_OR_MIGRATION
            )
        );
    }

    @ParameterizedTest
    @MethodSource("migration_args")
    void test_many_outputs_and_inputs_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, retiringFederation.getRedeemScript());
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValueInSatoshis();
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("migration_args")
    void test_many_outputs_and_one_input_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, retiringFederation.getRedeemScript());

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValueInSatoshis();
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    @ParameterizedTest
    @MethodSource("migration_args")
    void test_one_outputs_and_many_input_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        PegTxType expectedType
    ) {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, retiringFederation.getRedeemScript());
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValueInSatoshis();
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    // Flyover tests
    @Test
    void test_flyover() {
        // Arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withActivations(activations)
            .build();

        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addOutput(Coin.COIN, activeFederationAddress);

        btcTransaction.addInput(
            Sha256Hash.ZERO_HASH,
            0, new Script(new byte[]{})
        );

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            btcTransaction,
            blockNumberToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    @Test
    void test_flyover_segwit() {
        // Arrange
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        NetworkParameters btcTestNetParams = bridgeTestNetConstants.getBtcParams();
        Context context = new Context(bridgeTestNetConstants.getBtcParams());
        Context.propagate(context);

        int btcHeightWhenPegoutTxIndexActivates = bridgeTestNetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeTestNetConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;
        int blockNumberToStartUsingNewGeTransactionTypeMechanism = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;

        Federation retiringFederation = bridgeTestNetConstants.getGenesisFederation();

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        P2shErpFederation activeFederation = new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(signers),
            Instant.ofEpochMilli(1000L),
            0L,
            btcTestNetParams,
            bridgeTestNetConstants.getErpFedPubKeysList(),
            bridgeTestNetConstants.getErpFedActivationDelay(),
            activations
        );

        String segwitTxHex = "020000000001011f668117f2ca3314806ade1d99ae400f5413d7e9d4bfcbd11d52645e060e22fb0100000000fdffffff0300000000000000001b6a1952534b5401a27c6f697954357247e78f9900023cfe01a9d49c0412030000000000160014b413f59a7ee6e34321140e83ea661e0484a79bc2988708000000000017a9145e6cf80958803e9b3c81cd90422152520d2a505c870247304402203fce49b39f79581d93720f462b5f33f9174e66dc6efb635d4f41aacb33b08d0302201221aec5db31e269454fcc7a4df2936ccedd566ccf48828d4f97050954f196540121021831c5ba44b739521d635e521560525672087e4d5db053801f4aeb60e782f6d6d0f02400";
        BtcTransaction btcTransaction = new BtcTransaction(btcTestNetParams, Hex.decode(segwitTxHex));

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeTestNetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            blockNumberToStartUsingNewGeTransactionTypeMechanism
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
    }

    private static Stream<Arguments> old_fed_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
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
        BridgeConstants bridgeRegTestConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters btcRegTestsParams = bridgeRegTestConstants.getBtcParams();
        Context.propagate(new Context(btcRegTestsParams));

        final List<BtcECKey> REGTEST_OLD_FEDERATION_PRIVATE_KEYS = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
            BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
            BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        Federation oldFederation = createFederation(bridgeRegTestConstants, REGTEST_OLD_FEDERATION_PRIVATE_KEYS);
        Federation activeFederation = bridgeRegTestConstants.getGenesisFederation();

        Assertions.assertEquals(oldFederation.getAddress().toString(), bridgeRegTestConstants.getOldFederationAddress());

        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, oldFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(oldFederation, REGTEST_OLD_FEDERATION_PRIVATE_KEYS, migrationTx);

        Assertions.assertTrue(PegUtilsLegacy.txIsFromOldFederation(migrationTx, oldFederation.address));

        int blockNumberToStartUsingNewGeTransactionTypeMechanism = 0;
        if (shouldUsePegoutTxIndex) {
            int btcHeightWhenPegoutTxIndexActivates = bridgeRegTestConstants.getBtcHeightWhenPegoutTxIndexActivates();
            int pegoutTxIndexGracePeriodInBtcBlocks = bridgeRegTestConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;;
            blockNumberToStartUsingNewGeTransactionTypeMechanism = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
        }

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeRegTestConstants,
            activeFederation,
            null,
            migrationTx,
            blockNumberToStartUsingNewGeTransactionTypeMechanism
        );

        // Assert
        if (shouldUsePegoutTxIndex){
            verify(provider, never()).getLastRetiredFederationP2SHScript();
        } else {
            verify(provider, times(1)).getLastRetiredFederationP2SHScript();
        }
        Assertions.assertEquals(expectedType, transactionType);
    }

    private static Stream<Arguments> retired_fed_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGOUT_OR_MIGRATION
            ),
            Arguments.of(
                tbdActivations,
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
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        Federation retiredFederation = createFederation(bridgeMainnetConstants, fedKeys);
        Federation activeFederation = bridgeMainnetConstants.getGenesisFederation();

        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.of(retiredFederation.getP2SHScript()));

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, retiredFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFederation, fedKeys, migrationTx);

        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx);
        Assertions.assertTrue(firstInputSigHash.isPresent());

        when(provider.hasPegoutTxSigHash(firstInputSigHash.get())).thenReturn(true);

        int blockNumberToStartUsingNewGeTransactionTypeMechanism = 0;
        if (shouldUsePegoutTxIndex) {
            int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
            int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;;
            blockNumberToStartUsingNewGeTransactionTypeMechanism = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
        }

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            migrationTx,
            blockNumberToStartUsingNewGeTransactionTypeMechanism
        );

        // Assert
        if (shouldUsePegoutTxIndex){
            verify(provider, never()).getLastRetiredFederationP2SHScript();
        } else {
            verify(provider, times(1)).getLastRetiredFederationP2SHScript();
        }
        Assertions.assertEquals(expectedType, transactionType);
    }

    private static Stream<Arguments> retired_fed_no_existing_in_the_storage_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
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
        BridgeConstants bridgeRegTestConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters btcRegTestsParams = bridgeRegTestConstants.getBtcParams();
        Context.propagate(new Context(btcRegTestsParams));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        Federation retiredFederation = createFederation(bridgeRegTestConstants, fedKeys);
        Federation activeFederation = bridgeRegTestConstants.getGenesisFederation();

        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.empty());

        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);
        migrationTx.addInput(BitcoinTestUtils.createHash(1), 0, retiredFederation.getRedeemScript());
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFederation, fedKeys, migrationTx);

        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx);
        Assertions.assertTrue(firstInputSigHash.isPresent());

        when(provider.hasPegoutTxSigHash(firstInputSigHash.get())).thenReturn(true);

        int blockNumberToStartUsingNewGeTransactionTypeMechanism = 0;
        if (shouldUsePegoutTxIndex) {
            int btcHeightWhenPegoutTxIndexActivates = bridgeRegTestConstants.getBtcHeightWhenPegoutTxIndexActivates();
            int pegoutTxIndexGracePeriodInBtcBlocks = bridgeRegTestConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;;
            blockNumberToStartUsingNewGeTransactionTypeMechanism = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
        }

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeRegTestConstants,
            activeFederation,
            null,
            migrationTx,
            blockNumberToStartUsingNewGeTransactionTypeMechanism
        );

        // Assert
        if (shouldUsePegoutTxIndex){
            verify(provider, never()).getLastRetiredFederationP2SHScript();
        } else {
            verify(provider, times(1)).getLastRetiredFederationP2SHScript();
        }
        Assertions.assertEquals(expectedType, transactionType);
    }
}
