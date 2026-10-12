package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import java.util.Arrays;
import java.util.Optional;

/**
 * Refund output types accepted by peg-in instructions version 2.
 * Each type fixes the program length and the output script the Bridge builds from it.
 */
public enum PeginRefundScriptType {
    P2PKH(1, 20),
    P2SH(2, 20),
    P2WPKH(3, 20),
    P2WSH(4, 32),
    P2TR(5, 32);

    private final int typeByte;
    private final int programLength;

    PeginRefundScriptType(int typeByte, int programLength) {
        this.typeByte = typeByte;
        this.programLength = programLength;
    }

    public int getTypeByte() {
        return typeByte;
    }

    public int getProgramLength() {
        return programLength;
    }

    public boolean isLegacy() {
        return this == P2PKH || this == P2SH;
    }

    public static Optional<PeginRefundScriptType> fromTypeByte(int typeByte) {
        return Arrays.stream(values())
            .filter(type -> type.typeByte == typeByte)
            .findFirst();
    }

    public Script buildOutputScript(byte[] program) {
        if (program.length != programLength) {
            throw new IllegalArgumentException(String.format(
                "Invalid program length for %s. Expected %d bytes, received %d", this, programLength, program.length));
        }

        return switch (this) {
            case P2PKH -> new ScriptBuilder()
                .op(ScriptOpCodes.OP_DUP)
                .op(ScriptOpCodes.OP_HASH160)
                .data(program)
                .op(ScriptOpCodes.OP_EQUALVERIFY)
                .op(ScriptOpCodes.OP_CHECKSIG)
                .build();
            case P2SH -> ScriptBuilder.createP2SHOutputScript(program);
            case P2WPKH, P2WSH -> new ScriptBuilder().smallNum(0).data(program).build();
            case P2TR -> new ScriptBuilder().smallNum(1).data(program).build();
        };
    }
}
