package co.rsk.peg;

import static co.rsk.peg.PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static co.rsk.peg.PegTestUtils.createBech32Output;
import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PegUtilsGetTransactionTypeTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

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
        retiredFed = createP2shErpFederation(bridgeMainnetConstants, retiredFedSigners);

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa06", "fa07", "fa08"}, true
        );
        retiringFederation = createP2shErpFederation(bridgeMainnetConstants, retiringFedSigners);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        heightAtWhichToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private static Stream<Arguments> unknown_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
                tbdActivations,
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
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                false,
                true,
                PegTxType.PEGIN
            ),

            Arguments.of(
                tbdActivations,
                true,
                false,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                true,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
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

        if (existsRetiringFederation){
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiringFederation.getP2SHScript()));
        } else {
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiredFed.getP2SHScript()));
        }

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        btcTransaction.addOutput(shouldSendAmountBelowMinimum? belowMinimumPeginTxValue: minimumPeginTxValue, userAddress);

        // Act
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            existsRetiringFederation? retiringFederation: null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedPegTxType, pegTxType);
    }

    // Pegin tests
    private static Stream<Arguments> pegin_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedTxType, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    private static Stream<Arguments> pegin_retired_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
    void pegin_retired_fed(
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
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    @Test
    void anyAddressToAnyAddress_pegin_before_RSKIP379() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(minimumPeginTxValue, new Script(new byte[]{}) );

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

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

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

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
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(minimumPeginTxValue, new Script(new byte[]{}) );

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            activeFederation,
            null,
            anyToAnyTx,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }

    @Test
    void unknown_anyAddressToAnyAddress_below_minimum_unknown_after_RSIP379_and_using_pegout_tx_index() {
        // Arrange
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Coin belowMinimum = bridgeMainnetConstants.getMinimumPeginTxValue(activations).minus(Coin.SATOSHI);

        BtcTransaction anyToAnyTx = new BtcTransaction(btcMainnetParams);
        anyToAnyTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        anyToAnyTx.addOutput(belowMinimum, new Script(new byte[]{}));

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            mock(BridgeStorageProvider.class),
            bridgeMainnetConstants,
            activeFederation,
            null,
            anyToAnyTx,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, transactionType);
    }

    private static Stream<Arguments> sending_funds_below_minimum_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

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
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
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

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

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
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, transactionType);
    }

    private static Stream<Arguments> sending_funds_equal_or_above_to_minimum_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
    @MethodSource("sending_funds_equal_or_above_to_minimum_args")
    void sending_funds_equal_to_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

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
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    @ParameterizedTest
    @MethodSource("sending_funds_equal_or_above_to_minimum_args")
    void sending_funds_above_minimum_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).add(Coin.CENT);

        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

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
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, transactionType);
    }

    // Pegout tests

    private static Stream<Arguments> pegout_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.UNKNOWN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
    }

    // Migration tests
    @Test
    void migration() {
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
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    @Test
    void migration_sighash_no_exists_in_provider() {
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
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGIN, pegTxType);
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
            activeFederation,
            null,
            btcTransaction,
            heightAtWhichToStartUsingPegoutIndex
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    private static Stream<Arguments> migration_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedType, pegTxType);
    }

    // Flyover tests
    private static Stream<Arguments> flyover_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
                tbdActivations,
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
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                true,
                true,
                PegTxType.PEGIN
            ),
            Arguments.of(
                tbdActivations,
                false,
                false,
                true,
                PegTxType.PEGIN
            ),

            Arguments.of(
                tbdActivations,
                true,
                false,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                false,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                true,
                PegTxType.UNKNOWN
            ),
            Arguments.of(
                tbdActivations,
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
        if (existsRetiringFederation){
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiringFederation.getP2SHScript()));
        } else {
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiredFed.getP2SHScript()));
        }

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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            existsRetiringFederation? retiringFederation: null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedPegTxType, pegTxType);
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
        if (existsRetiringFederation){
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiringFederation.getP2SHScript()));
        } else {
            when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.ofNullable(retiredFed.getP2SHScript()));
        }

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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            existsRetiringFederation? retiringFederation: null,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(expectedPegTxType, pegTxType);
    }

    private static Stream<Arguments> flyover_migration_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        migrationTx.setWitness(0, txWitness);

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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        migrationTx.setWitness(0, txWitness);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
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

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        btcTransaction.setWitness(0, txWitness);
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
        PegTxType pegTxType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            retiringFederation,
            btcTransaction,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex : 0
        );

        // Assert
        Assertions.assertEquals(PegTxType.PEGOUT_OR_MIGRATION, pegTxType);
    }

    // old fed

    private static Stream<Arguments> old_fed_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants);

        Assertions.assertEquals(oldFederation.getAddress().toString(), bridgeRegTestConstants.getOldFederationAddress());

        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);

        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, oldFederation.getRedeemScript())
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(oldFederation, REGTEST_OLD_FEDERATION_PRIVATE_KEYS, migrationTx);

        Assertions.assertTrue(PegUtilsLegacy.txIsFromOldFederation(migrationTx, oldFederation.getAddress()));

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeRegTestConstants,
            activeFederation,
            null,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex: 0
        );

        // Assert
        if (shouldUsePegoutTxIndex){
            verify(provider, never()).getLastRetiredFederationP2SHScript();
        } else {
            verify(provider, times(1)).getLastRetiredFederationP2SHScript();
        }
        Assertions.assertEquals(expectedType, transactionType);
    }

    // retired fed

    private static Stream<Arguments> retired_fed_to_live_fed_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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

        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.of(retiredFed.getDefaultP2SHScript()));

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);

        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiredFed.getRedeemScript())
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFed, retiredFedSigners, migrationTx);

        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx);
        Assertions.assertTrue(firstInputSigHash.isPresent());

        when(provider.hasPegoutTxSigHash(firstInputSigHash.get())).thenReturn(true);

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex: 0
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
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

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
        Context.propagate(new Context(btcMainnetParams));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        Federation retiredFederation = createFederation(bridgeMainnetConstants, fedKeys);
        Federation activeFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.empty());

        BtcTransaction migrationTx = new BtcTransaction(btcMainnetParams);

        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiredFederation.getRedeemScript())
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiredFederation, fedKeys, migrationTx);

        Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx);
        Assertions.assertTrue(firstInputSigHash.isPresent());

        when(provider.hasPegoutTxSigHash(firstInputSigHash.get())).thenReturn(true);

        // Act
        PegTxType transactionType = PegUtils.getTransactionType(
            activations,
            provider,
            bridgeMainnetConstants,
            activeFederation,
            null,
            migrationTx,
            shouldUsePegoutTxIndex? heightAtWhichToStartUsingPegoutIndex: 0
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
