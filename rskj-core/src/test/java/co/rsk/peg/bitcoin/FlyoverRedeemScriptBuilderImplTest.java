package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static co.rsk.peg.bitcoin.FlyoverRedeemScriptCreationException.Reason.INVALID_FLYOVER_DERIVATION_HASH;
import static org.junit.jupiter.api.Assertions.*;

class FlyoverRedeemScriptBuilderImplTest {
    private static final Sha256Hash zeroHash = Sha256Hash.ZERO_HASH;
    private static final int OP_DROP_CODE = 117;
    private final FlyoverRedeemScriptBuilder flyoverRedeemScriptBuilder = new FlyoverRedeemScriptBuilderImpl();
    private Script redeemScript;

    @BeforeEach
    void setUp() {
        Federation federation = new P2shErpFederationBuilder().build();
        redeemScript = federation.getRedeemScript();
    }

    @ParameterizedTest
    @MethodSource("invalidDerivationHashArgsProvider")
    void addFlyoverDerivationHashToRedeemScript_withInvalidDerivationHash_shouldThrowFlyoverRedeemScriptCreationException(Sha256Hash flyoverDerivationHash) {
        FlyoverRedeemScriptCreationException exception = assertThrows(FlyoverRedeemScriptCreationException.class,
            () -> flyoverRedeemScriptBuilder.addFlyoverDerivationHashToRedeemScript(flyoverDerivationHash, redeemScript));

        assertEquals(INVALID_FLYOVER_DERIVATION_HASH, exception.getReason());

        String expectedMessage = "Provided flyover derivation hash is invalid.";
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static Stream<Arguments> invalidDerivationHashArgsProvider() {
        return Stream.of(Arguments.of(null, zeroHash));
    }

    @Test
    void addFlyoverDerivationHashToRedeemScript_withValidDerivationHash_shouldReturnRedeemScriptWithFlyoverPrefix() {
        // arrange
        Sha256Hash flyoverDerivationHash = BitcoinTestUtils.createHash(1);

        // act
        Script redeemScriptWithFlyoverPrefix = flyoverRedeemScriptBuilder.addFlyoverDerivationHashToRedeemScript(flyoverDerivationHash, redeemScript);

        // assert
        List<ScriptChunk> originalRedeemScriptChunks = getOriginalRedeemScriptChunks(redeemScriptWithFlyoverPrefix);
        assertEquals(redeemScript.getChunks(), originalRedeemScriptChunks);

        List<ScriptChunk> flyoverPrefixChunks = getFlyoverChunks(redeemScriptWithFlyoverPrefix);
        assertArrayEquals(flyoverDerivationHash.getBytes(), flyoverPrefixChunks.get(0).data);
        assertEquals(OP_DROP_CODE, flyoverPrefixChunks.get(1).opcode);
    }

    private List<ScriptChunk> getOriginalRedeemScriptChunks(Script redeemScript) {
        List<ScriptChunk> redeemScriptChunks = redeemScript.getChunks();
        int firstChunkAfterFlyoverIndex = 2;
        int lastChunkIndex = redeemScriptChunks.size();

        return redeemScriptChunks.subList(firstChunkAfterFlyoverIndex, lastChunkIndex);
    }

    private List<ScriptChunk> getFlyoverChunks(Script redeemScript) {
        List<ScriptChunk> redeemScriptChunks = redeemScript.getChunks();
        int firstFlyoverChunk = 0;
        int lastFlyoverChunk = 2;

        return redeemScriptChunks.subList(firstFlyoverChunk, lastFlyoverChunk);
    }
}
