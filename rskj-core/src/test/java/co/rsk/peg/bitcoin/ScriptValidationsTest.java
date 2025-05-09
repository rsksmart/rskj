package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPTSIG_ELEMENT_SIZE;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_FOR_WITNESS_SIZE;

class ScriptValidationsTest {

    @Test
    void validateSizeOfRedeemScriptForScriptSig_withinLimit_shouldPass() {
        byte[] program = new byte[(int)Script.MAX_SCRIPT_ELEMENT_SIZE];
        Script script = new Script(program);
        assertDoesNotThrow(() -> ScriptValidations.validateSizeOfRedeemScriptForScriptSig(script));
    }

    @Test
    void validateSizeOfRedeemScriptForScriptSig_exceedsLimit_shouldThrow() {
        byte[] program = new byte[(int)Script.MAX_SCRIPT_ELEMENT_SIZE + 1];
        Script script = new Script(program);

        ScriptCreationException ex = assertThrows(ScriptCreationException.class, () ->
            ScriptValidations.validateSizeOfRedeemScriptForScriptSig(script)
        );
        assertEquals(ABOVE_MAX_SCRIPTSIG_ELEMENT_SIZE, ex.getReason());
    }

    @Test
    void validateSizeOfRedeemScriptForWitness_withinLimit_shouldPass() {
        byte[] program = new byte[(int)Script.MAX_STANDARD_P2WSH_SCRIPT_SIZE];
        Script script = new Script(program);
        assertDoesNotThrow(() ->
            ScriptValidations.validateSizeOfRedeemScriptForWitness(script)
        );
    }

    @Test
    void validateSizeOfRedeemScriptForWitness_exceedsLimit_shouldThrow() {
        byte[] program = new byte[(int)Script.MAX_STANDARD_P2WSH_SCRIPT_SIZE + 1];
        Script script = new Script(program);
        ScriptCreationException ex = assertThrows(ScriptCreationException.class, () ->
            ScriptValidations.validateSizeOfRedeemScriptForWitness(script)
        );
        assertEquals(ABOVE_MAX_SCRIPT_FOR_WITNESS_SIZE, ex.getReason());
    }
}
