package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_FLYOVER_DERIVATION_HASH;
import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_INTERNAL_REDEEM_SCRIPTS;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.RedeemScriptParser.MultiSigType;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import java.util.List;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlyoverRedeemScriptBuilderImplTest {

    private static NetworkParameters mainNetParams = BridgeMainNetConstants.getInstance().getBtcParams();
    private static NetworkParameters testNetParams = BridgeTestNetConstants.getInstance().getBtcParams();
    private static ErpFederation erpFederation = FederationTestUtils.getErpFederation(testNetParams);

    private FlyoverRedeemScriptBuilder flyoverRedeemScriptBuilder;

    @BeforeEach
    void setUp() {
        flyoverRedeemScriptBuilder = FlyoverRedeemScriptBuilderImpl.builder();
    }

    @ParameterizedTest
    @MethodSource("invalidDerivationHashArgsProvider")
    void of_invalidPrefix_shouldThrowRedeemScriptCreationException(Keccak256 flyoverDerivationHash) {
        Script redeemScript = P2shErpFederationBuilder.builder().build().getRedeemScript();

        RedeemScriptCreationException exception = assertThrows(
            RedeemScriptCreationException.class,
            () -> flyoverRedeemScriptBuilder.of(flyoverDerivationHash, redeemScript)
        );

        assertEquals(INVALID_FLYOVER_DERIVATION_HASH, exception.getReason());

        String expectedMessage = String.format("Provided flyover derivation hash %s is invalid.", flyoverDerivationHash);
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static Stream<Keccak256> invalidDerivationHashArgsProvider() {
        return Stream.of(null, Keccak256.ZERO_HASH);
    }

    @ParameterizedTest
    @MethodSource("validRedeemScriptsArgsProvider")
    void of_whenValidInternalRedeemScript_shouldReturnFlyoverRedeemScript(Script internalRedeemScript) {
        // arrange
        Keccak256 flyoverDerivationHash = RskTestUtils.createHash(1);

        // act
        Script flyoverRedeemScript = flyoverRedeemScriptBuilder.of(flyoverDerivationHash, internalRedeemScript);

        // assert
        List<ScriptChunk> originalRedeemScriptChunks = getOriginalRedeemScriptChunks(flyoverRedeemScript);
        List<ScriptChunk> flyoverRedeemScriptChunks = flyoverRedeemScript.getChunks();

        ScriptChunk flyoverDerivationHashChunk = flyoverRedeemScriptChunks.get(0);
        ScriptChunk opDropChunk = flyoverRedeemScriptChunks.get(1);
        MultiSigType multiSigType = RedeemScriptParserFactory.get(flyoverRedeemScriptChunks)
            .getMultiSigType();

        assertEquals(MultiSigType.FLYOVER, multiSigType);
        assertEquals(internalRedeemScript.getChunks(), originalRedeemScriptChunks);
        assertArrayEquals(flyoverDerivationHash.getBytes(), flyoverDerivationHashChunk.data);
        assertEquals(ScriptOpCodes.OP_DROP, opDropChunk.opcode);

    }

    private static Stream<Arguments> validRedeemScriptsArgsProvider() {
        ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
        Script nonStandardErpRedeemScriptForHop = NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(
            hopActivations,
            testNetParams
        ).of(
            erpFederation.getBtcPublicKeys(),
            erpFederation.getNumberOfEmergencySignaturesRequired(),
            erpFederation.getErpPubKeys(),
            erpFederation.getNumberOfEmergencySignaturesRequired(),
            erpFederation.getActivationDelay()
        );
        Script nonStandardErpRedeemScriptForTestnetHop = NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(
            hopActivations,
            testNetParams
        ).of(
            erpFederation.getBtcPublicKeys(),
            erpFederation.getNumberOfEmergencySignaturesRequired(),
            erpFederation.getErpPubKeys(),
            erpFederation.getNumberOfEmergencySignaturesRequired(),
            erpFederation.getActivationDelay()
        );
        Script nonStandardErpRedeemScriptAllActivations = NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(
            allActivations,
            mainNetParams
        ).of(
            erpFederation.getBtcPublicKeys(),
            erpFederation.getNumberOfEmergencySignaturesRequired(),
            erpFederation.getErpPubKeys(),
            erpFederation.getNumberOfEmergencySignaturesRequired(),
            erpFederation.getActivationDelay()
        );

        Script standardRedeemScript = StandardMultiSigFederationBuilder.builder().build()
            .getRedeemScript();
        Script p2shRedemptionScript = P2shErpFederationBuilder.builder().build().getRedeemScript();

        return Stream.of(
            Arguments.of(nonStandardErpRedeemScriptForHop),
            Arguments.of(nonStandardErpRedeemScriptForTestnetHop),
            Arguments.of(nonStandardErpRedeemScriptAllActivations),
            Arguments.of(standardRedeemScript),
            Arguments.of(p2shRedemptionScript)
        );
    }

    private List<ScriptChunk> getOriginalRedeemScriptChunks(Script redeemScript) {
        List<ScriptChunk> redeemScriptChunks = redeemScript.getChunks();
        int firstOriginalChunkIndex = 2;
        int lastOriginalChunkIndex = redeemScriptChunks.size();

        return redeemScriptChunks.subList(firstOriginalChunkIndex, lastOriginalChunkIndex);
    }

    @ParameterizedTest
    @MethodSource("invalidRedeemScriptsArgsProvider")
    void of_whenInvalidRedeemScript_shouldThrowRedeemScriptCreationException(
        Script internalRedeemScript, String expectedMessage) {
        // arrange
        Keccak256 flyoverDerivationHash = RskTestUtils.createHash(1);

        RedeemScriptCreationException exception = assertThrows(
            RedeemScriptCreationException.class,
            () -> flyoverRedeemScriptBuilder.of(flyoverDerivationHash, internalRedeemScript)
        );

        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());

        assertEquals(expectedMessage, exception.getMessage());

    }

    private static Stream<Arguments> invalidRedeemScriptsArgsProvider() {
        Script p2shRedemptionScript = P2shErpFederationBuilder.builder().build().getRedeemScript();

        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            RskTestUtils.createHash(1),
            p2shRedemptionScript
        );

        Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(p2shRedemptionScript);
        Script scriptSig = p2SHOutputScript.createEmptyInputScript(null, p2shRedemptionScript);
        Script emptyScript = new Script(new byte[]{});

        return Stream.of(
            Arguments.of(flyoverRedeemScript, "Provided redeem script cannot be a flyover redeem script."),
            Arguments.of(p2SHOutputScript, "Provided redeem script has an invalid structure."),
            Arguments.of(scriptSig, "Provided redeem script has an invalid structure."),
            Arguments.of(emptyScript, "Provided redeem script has an invalid structure."),
            Arguments.of(null, "Provided redeem script is null.")
        );
    }
}
