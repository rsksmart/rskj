package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_FLYOVER_DERIVATION_HASH;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import java.util.List;
import java.util.stream.Stream;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FlyoverRedeemScriptBuilderImplTest {
    private Script redeemScript;
    private FlyoverRedeemScriptBuilder flyoverRedeemScriptBuilder;

    @BeforeEach
    void setUp() {
        Federation federation = P2shErpFederationBuilder.builder().build();
        redeemScript = federation.getRedeemScript();
        flyoverRedeemScriptBuilder = FlyoverRedeemScriptBuilderImpl.builder();
    }

    @ParameterizedTest
    @MethodSource("invalidDerivationHashArgsProvider")
    void addFlyoverDerivationHashToRedeemScript_withInvalidPrefix_shouldThrowFlyoverRedeemScriptCreationException(Keccak256 flyoverDerivationHash) {
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

    @Test
    void addFlyoverDerivationHashToRedeemScript_shouldReturnRedeemScriptWithFlyoverDerivationHash() {
        // arrange
        Keccak256 flyoverDerivationHash = TestUtils.generateHash("hash");

        // act
        Script redeemScriptWithFlyoverDerivationHash = flyoverRedeemScriptBuilder.of(flyoverDerivationHash, redeemScript);

        // assert
        List<ScriptChunk> originalRedeemScriptChunks = getOriginalRedeemScriptChunks(redeemScriptWithFlyoverDerivationHash);
        assertEquals(redeemScript.getChunks(), originalRedeemScriptChunks);

        List<ScriptChunk> redeemScriptWithFlyoverDerivationHashChunks = redeemScriptWithFlyoverDerivationHash.getChunks();
        ScriptChunk flyoverDerivationHashChunk = redeemScriptWithFlyoverDerivationHashChunks.get(0);
        ScriptChunk opDropChunk = redeemScriptWithFlyoverDerivationHashChunks.get(1);
        assertArrayEquals(flyoverDerivationHash.getBytes(), flyoverDerivationHashChunk.data);
        assertEquals(ScriptOpCodes.OP_DROP, opDropChunk.opcode);
    }

    private List<ScriptChunk> getOriginalRedeemScriptChunks(Script redeemScript) {
        List<ScriptChunk> redeemScriptChunks = redeemScript.getChunks();
        int firstOriginalChunkIndex = 2;
        int lastOriginalChunkIndex = redeemScriptChunks.size();

        return redeemScriptChunks.subList(firstOriginalChunkIndex, lastOriginalChunkIndex);
    }
}
