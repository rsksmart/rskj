/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.lll;

import co.rsk.asm.EVMDissasembler;
import co.rsk.lll.asm.CodeBlock;
import co.rsk.lll.asm.EVMAssemblerHelper;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Sergio on 24/10/2016.
 */
public class LLLTest {

    void showCode(byte[] code) {
        System.out.println("code: " + ByteUtil.toHexString(code));
        System.out.println("dissasemble:");
        System.out.println(EVMDissasembler.getDissasemble(code));
    }
    void showCodeBlock(EVMAssemblerHelper helper, CodeBlock block) {
        System.out.println("code: " + ByteUtil.toHexString(block.getCode()));
        System.out.println("dissasemble:");
        EVMDissasembler dis = new EVMDissasembler();
        System.out.println(dis.dissasembleCodeBlock(block,helper));
    }

    public void compileTest(String t) throws LLLCompilationError {
        byte[] code;
        System.out.println("expr: "+t);
        LLLCompiler c= new LLLCompiler();
        c.compile(t);

        CodeBlock block = c.getCodeBlock();
        EVMAssemblerHelper helper = c.getHelper();
        //showCode(code);
        showCodeBlock(helper,block);
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

     public void compileTestFile(String name) throws LLLCompilationError {
         try {
             String fromFile = readFile(name, StandardCharsets.UTF_8);
             compileTest(fromFile);
         } catch (IOException e) {

             e.printStackTrace();
         }
     }

    void showCurDir() {
        String current = null;
        try {
            current = new java.io.File(".").getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Current dir:" + current);
    }
    @Test
    public void DissasembleThisPlease() {
        String dis =EVMDissasembler.getDissasemble(Hex.decode("600060006000600060026002600803036002600306600260020460046004600402026002600201010101013360c85a03f1"));
        System.out.println(dis);
    }

    @Test
    public void lllcompileTest() {

        try {


            compileTest("(FOR {[i] 0} (< @i 10) {[@i] 0}");


            compileTest("(WHILE (< @i 10) {[@i] 0})");

            if (false) {
                compileTest("(LIT 0 \"StringToStore\")");

                compileTest("(SEQ (INCLUDE \"LLLTests/t1.lll\") (- 0x01 0x02))");

                compileTest("(SUB 8 2 2)");

                //compileTest("(CALL 1 2 3 4 5 6 7 8 9 10 11 12)"); // this must rise a compiler exception

                compileTestFile("LLLTests/t1.lll");

                // Constants
                compileTest("{ (def 'halve  0x20fb79e7) (halve) }");

                compileTest("(- 0x01 0x02)");

                // Testing comments
                compileTest(
                        "{ (ADD 1 1) ; this is a comment\n" +
                                "(ADD 0x5 0x5)\n" +
                                "; another comment\n" +
                                "}"
                );
                compileTest(
                        "{ (DEF 'SQR ($x) (MUL $x $x)) " +
                                "(SQR (ADD 0x5 0x5)) " +
                                "}"
                );
                compileTest(
                        "{ (DEF 'SQR ($x) (MUL $x $x)) " +
                                "(DEF 'SQRSUM ($x $y) (+ (SQR $x) $y)) " +
                                "(SQRSUM 0x5 0x01) " +
                                "}"
                );


                compileTest("(IF 0x01 0x02 0x03)");
                compileTest("(IF 0x01 (REVERT) 0x03)"); // if REVERT code-size optimization

                compileTest("(SEND 0x01 100)"); // Sends 100 wit-sbtc to account 0x01
                compileTest("(UNLESS (SEND 0x01 100) (REVERT))"); // Sends 100 wit-sbtc to account 0x01


                compileTest("(ASM 0x01 0x02 ADD)");
                compileTest("(ASM 1 2 ADD)");   // numerical constants (non-hex)
                compileTest("(WITH ($1 $2) (0xff 0xf0) (ADD $1 $2))"); // mult-variable WITH

                compileTest(
                        "{ (FUNC SQR ($x) (MUL $x $x)) (FUNC SQRSUM ($x $y) (+ (SQR $x) $y)) " +
                                "(SQRSUM 0x5 0x01) " +
                                "}"
                );

                compileTest("(UNLESS 0x01 0x02)");

                compileTest("(ADD 0x03 (ADD 0x01 0x02)))");

                compileTest("(WHEN 0x01 0x02)");

                compileTest("(WITH $1 0xFF (WITH $1 0x80 $1))"); // test rename
                compileTest("(WITH $1 0xFF (ADD $1 (SUB $1 $1)))");

                compileTest("(IF 0x01 0x02)");
                compileTest("(&& 0x02 0x01)");
                compileTest("(|| 0x02 0x01)");

                compileTest("(>= 0x02 0x01)");
                compileTest("(+ 0x02 0x01)");
                compileTest("(> 0x02 0x01)");

                compileTest("(<= 0x02 0x01)");
                compileTest("(!= 0x02 0x01)");

                compileTest("{ " +
                        "[x0] 0 \n" +
                        "[x1] 1 \n" +
                        "[x2] 2 \n" +
                        "[x2] (ADD @x2 0x01)\n" +
                        "}"); // mem[x] = 0; mem[x] = mem[x]+1
                compileTest("[[0]] (ADD @@0 0x01)"); // pmem[0] = pmem[0]+1
                compileTest("[0] (ADD @0 0x01)"); // mem[0] = mem[0]+1
                compileTest("[[0]] 0x01");
                compileTest("[0] 0x01");
                compileTest("(ADD 0x01 0x02)");
                compileTest("(ADD (ADD 0x01 0x02) 0x03))");

            }

        } catch (LLLCompilationError lllCompilationError) {
            lllCompilationError.printStackTrace();
        }

        //Assertions.assertEquals(0, ...);
    }
}
