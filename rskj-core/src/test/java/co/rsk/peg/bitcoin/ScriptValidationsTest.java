package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;
import org.junit.jupiter.api.Test;

import static co.rsk.peg.bitcoin.ScriptValidations.*;
import static org.junit.jupiter.api.Assertions.*;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPTSIG_ELEMENT_SIZE;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_FOR_WITNESS_SIZE;

class ScriptValidationsTest {

    @Test
    void validateSizeOfRedeemScriptForScriptSig_withinLimit_shouldPass() {
        byte[] program = new byte[(int) MAX_P2SH_REDEEM_SCRIPT_SIZE];
        Script redeemScript = new Script(program);
        assertDoesNotThrow(() -> ScriptValidations.validateSizeOfRedeemScriptForScriptSig(redeemScript));
    }

    @Test
    void validateSizeOfRedeemScriptForScriptSig_exceedsLimit_shouldThrow() {
        byte[] program = new byte[(int) MAX_P2SH_REDEEM_SCRIPT_SIZE + 1];
        Script redeemScript = new Script(program);

        ScriptCreationException ex = assertThrows(ScriptCreationException.class, () ->
            ScriptValidations.validateSizeOfRedeemScriptForScriptSig(redeemScript)
        );
        assertEquals(ABOVE_MAX_SCRIPTSIG_ELEMENT_SIZE, ex.getReason());
    }

    @Test
    void validateSizeOfRedeemScriptForWitness_withinLimit_shouldPass() {
        byte[] program = new byte[(int) MAX_P2WSH_REDEEM_SCRIPT_SIZE];
        Script redeemScript = new Script(program);
        assertDoesNotThrow(() ->
            ScriptValidations.validateSizeOfRedeemScriptForWitness(redeemScript)
        );
    }

    @Test
    void validateSizeOfRedeemScriptForWitness_exceedsLimit_shouldThrow() {
        byte[] program = new byte[(int) MAX_P2WSH_REDEEM_SCRIPT_SIZE + 1];
        Script redeemScript = new Script(program);
        ScriptCreationException ex = assertThrows(ScriptCreationException.class, () ->
            ScriptValidations.validateSizeOfRedeemScriptForWitness(redeemScript)
        );
        assertEquals(ABOVE_MAX_SCRIPT_FOR_WITNESS_SIZE, ex.getReason());
    }
}
