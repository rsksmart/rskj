package co.rsk.util;

import co.rsk.asm.EVMAssembler;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Created by SerAdmin on 3/20/2018.
 */
public class AssemblerTest {
    @Test
    public void assemblerTest1() throws IOException, InterruptedException {


        String asm ="0x01 label1: JUMPDEST @label1 JUMPI";

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);
        byte[] compilerCode = {
                96, // PUSH1
                1,  // 0x01
                91, // JUMPDEST
                99, // PUSH4
                0,
                0,
                0,
                2,  // Offset label
                87}; // JUMPI

        Assert.assertArrayEquals(code,compilerCode);

    }
}
