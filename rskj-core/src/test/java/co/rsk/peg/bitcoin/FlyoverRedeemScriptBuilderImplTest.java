package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import org.ethereum.TestUtils;
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
    private static final Keccak256 zeroHash = Keccak256.ZERO_HASH;
    private static final int OP_DROP_CODE = 117;
    private Script redeemScript;
    private FlyoverRedeemScriptBuilder flyoverRedeemScriptBuilder;

    @BeforeEach
    void setUp() {
        Federation federation = new P2shErpFederationBuilder().build();
        redeemScript = federation.getRedeemScript();
        flyoverRedeemScriptBuilder = new FlyoverRedeemScriptBuilderImpl();
    }

    @ParameterizedTest
    @MethodSource("invalidDerivationHashArgsProvider")
    void addFlyoverDerivationHashToRedeemScript_withInvalidPrefix_shouldThrowFlyoverRedeemScriptCreationException(Keccak256 flyoverDerivationHash) {
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
    void addFlyoverDerivationHashToRedeemScript_shouldReturnRedeemScriptWithFlyoverDerivationHash() {
        // arrange
        Keccak256 flyoverDerivationHash = TestUtils.generateHash("hash");

        // act
        Script redeemScriptWithFlyoverDerivationHash = flyoverRedeemScriptBuilder.addFlyoverDerivationHashToRedeemScript(flyoverDerivationHash, redeemScript);

        // assert
        List<ScriptChunk> originalRedeemScriptChunks = getOriginalRedeemScriptChunks(redeemScriptWithFlyoverDerivationHash);
        assertEquals(redeemScript.getChunks(), originalRedeemScriptChunks);

        List<ScriptChunk> flyoverChunks = getFlyoverChunks(redeemScriptWithFlyoverDerivationHash);
        ScriptChunk flyoverDerivationHashChunk = flyoverChunks.get(0);
        ScriptChunk opDropChunk = flyoverChunks.get(1);
        assertArrayEquals(flyoverDerivationHash.getBytes(), flyoverDerivationHashChunk.data);
        assertEquals(OP_DROP_CODE, opDropChunk.opcode);
    }

    private List<ScriptChunk> getOriginalRedeemScriptChunks(Script redeemScript) {
        List<ScriptChunk> redeemScriptChunks = redeemScript.getChunks();
        int firstOriginalChunkIndex = 2;
        int lastOriginalChunkIndex = redeemScriptChunks.size();

        return redeemScriptChunks.subList(firstOriginalChunkIndex, lastOriginalChunkIndex);
    }

    private List<ScriptChunk> getFlyoverChunks(Script redeemScript) {
        List<ScriptChunk> redeemScriptChunks = redeemScript.getChunks();
        int firstFlyoverChunkIndex = 0;
        int lastFlyoverChunkIndex = 2;

        return redeemScriptChunks.subList(firstFlyoverChunkIndex, lastFlyoverChunkIndex);
    }
}
