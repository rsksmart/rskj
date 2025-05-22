package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_ELEMENT_SIZE;

class ScriptValidationsTest {

    @Test
    void validateRedeemScriptSize_withinLimit_shouldPass() {
        byte[] program = new byte[(int)Script.MAX_SCRIPT_ELEMENT_SIZE];
        Script script = new Script(program);
        assertDoesNotThrow(() -> ScriptValidations.validateRedeemScripSize(script));
    }

    @Test
    void validateRedeemScriptSize_exceedsLimit_shouldThrow() {
        byte[] program = new byte[(int)Script.MAX_SCRIPT_ELEMENT_SIZE + 1];
        Script script = new Script(program);

        ScriptCreationException ex = assertThrows(ScriptCreationException.class, () ->
            ScriptValidations.validateRedeemScripSize(script)
        );
        assertEquals(ABOVE_MAX_SCRIPT_ELEMENT_SIZE, ex.getReason());
    }

    @Test
    void validateP2WSHRedeemScriptSize_withinLimit_shouldPass() {
        int numberOfSignaturesRequired = 3;
        int scriptSize = (int) Script.MAX_STANDARD_P2WSH_SCRIPT_SIZE
            - (numberOfSignaturesRequired * Script.SIG_SIZE)
            - 2; // 1 byte for OP_CHECKMULTISIG bug, 1 for OP_NOTIF
        byte[] program = new byte[scriptSize];

        Script script = new Script(program);
        assertDoesNotThrow(() ->
            ScriptValidations.validateP2WSHRedeemScriptSize(script, numberOfSignaturesRequired)
        );
    }

    @Test
    void validateP2WSHRedeemScriptSize_exceedsLimit_shouldThrow() {
        int numberOfSignaturesRequired = 3;
        int scriptSize = (int) Script.MAX_STANDARD_P2WSH_SCRIPT_SIZE
            - (numberOfSignaturesRequired * Script.SIG_SIZE)
            - 2 + 1; // Just 1 byte over
        byte[] program = new byte[scriptSize];

        Script script = new Script(program);
        ScriptCreationException ex = assertThrows(ScriptCreationException.class, () ->
            ScriptValidations.validateP2WSHRedeemScriptSize(script, numberOfSignaturesRequired)
        );
        assertEquals(ABOVE_MAX_SCRIPT_ELEMENT_SIZE, ex.getReason());
    }
}
