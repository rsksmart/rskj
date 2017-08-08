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

package co.rsk.asm;

import co.rsk.lll.asm.CodeBlock;
import co.rsk.lll.asm.EVMAssemblerHelper;
import org.ethereum.util.Utils;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

/**
 * Created by Sergio on 25/10/2016.
 */
public class EVMDissasembler {
    // Does not yet support script versioning bytes
    private byte[] ops;
    private int pc;
    private boolean stopped;
    private byte exeVersion;
    private byte scriptVersion;
    private int startAddr;
    private Set<Integer> jumpdest = new HashSet<>();
    private boolean findCodeOffset = false;
    private int codeOffset = -1;
    private int codeOffsetState = 0;
    private boolean hexaOffsets =true;

    public void stop() {
        stopped = true;
    }

    public DataWord sweepGetDataWord(int n) {
        if (pc + n > ops.length) {
            stop();
            // In this case partial data is copied. At least Ethereumj does this
            // Asummes LSBs are zero. assignDataRange undestands this semantics.
        }

        DataWord dw = new DataWord();
        dw.assignDataRange(ops, pc, n);
        pc += n;
        if (pc >= ops.length) stop();

        return dw;
    }


    public static int getScriptVersionInCode(byte[] ops) {
        if (ops.length >= 4) {
            OpCode op = OpCode.code(ops[0]);
            if ((op != null) && op.equals(OpCode.HEADER)) {
                return (ops[2]);
            }
        }
        return 0;
    }

    public int processAndSkipCodeHeader(int i) {
        if (ops.length >= 4) {
            OpCode op = OpCode.code(ops[0]);
            if ((op != null) && op.equals(OpCode.HEADER)) {
                // next byte is executable format version
                // header length in bytes
                exeVersion = ops[1];
                scriptVersion = ops[2];
                byte extHeaderLen = ops[3];
                i += 4 + extHeaderLen;
                startAddr = i;
                pc = i;
            }
        }
        return i;
    }

    public void precompile() {
        int i = 0;
        exeVersion = 0;
        scriptVersion = 0;
        startAddr = 0;
        pc = 0;
        i = processAndSkipCodeHeader(i);
        computeJumpDests(i);

        // Start stopped ?
        if (pc >= ops.length) {
            stop();
        }
    }

    public void computeJumpDests(int i) {
        for (; i < ops.length; ++i) {
            OpCode op = OpCode.code(ops[i]);
            if (op == null) continue;

            if (op.equals(OpCode.JUMPDEST)) jumpdest.add(i);

            if (op.asInt() >= OpCode.PUSH1.asInt() && op.asInt() <= OpCode.PUSH32.asInt()) {
                i += op.asInt() - OpCode.PUSH1.asInt() + 1;
            }
        }
    }


    public static boolean isEthereumCompatibleScript(byte[] aprogramCode) {
        return getScriptVersionInCode(aprogramCode) == 0;
    }

    public byte getOp(int pc) {
        return (getLength(ops) <= pc) ? 0 : ops[pc];
    }

    public byte getCurrentOp() {
        return isEmpty(ops) ? 0 : ops[pc];
    }

    public int getPC() {
        return pc;
    }

    public void setPC(int pc) {
        this.pc = pc;

        if (this.pc >= ops.length) {
            stop();
        }
    }

    public void setHexaOffsets(boolean a ) {
        hexaOffsets =a;
    }

    public void setfindCodeOffset(boolean a) {
        findCodeOffset = a;
    }

    public boolean isStopped() {
        return stopped;
    }

    public static String getDissasemble(byte[] code) {
        EVMDissasembler d = new EVMDissasembler();
        return d.dissasemble(code);
    }

    byte[] code;
    CodeBlock block;
    EVMAssemblerHelper helper;
    boolean printOffsets = true;
    StringBuilder sb;
    int[] refIterator;
    int[] tagIterator;

    public String dissasembleCodeBlock(CodeBlock block, EVMAssemblerHelper helper) {
        this.code = block.getCode();
        this.block = block;
        this.helper = helper;
        return dissasembleInternal();

    }

    public String dissasemble(byte[] code) {
        this.code = code;
        return dissasembleInternal();
    }

    public String getVisualLabelName(int labIndex) {
        String ref = helper.getLabelName(labIndex);
        if (ref == null)
            ref = "label_" + Integer.toString(labIndex);
        return ref;
    }

    BigInteger printPush(OpCode op) {
        BigInteger bi = null;
        sb.append(' ').append(op.name()).append(' ');
        int nPush = op.val() - OpCode.PUSH1.val() + 1;
        String name = null;
        if ((block != null) && (helper != null) && (nPush == 4)) { // optimization, only find int32 label refs
            int labelId = block.getTagIdByPos(pc+1, tagIterator);
            if (labelId >= 0) {
                name = getVisualLabelName(labelId);
                sb.append(name);
                if (printOffsets)
                    sb.append(" ;");
            }
        }

        if ((printOffsets) || (name==null)) {
            byte[] data = Arrays.copyOfRange(code, pc + 1, pc + nPush + 1);
            bi = new BigInteger(1, data);
            sb.append("0x").append(bi.toString(16));
            if (bi.bitLength() <= 32) {
                sb.append(" (").append(new BigInteger(1, data).toString()).append(") ");
            }
        }
        return bi;
    }

    void printCodeSections() {
        boolean newLine = false;
        if (helper != null) {
            int labIndex = helper.findLabelByPos(pc);
            if (labIndex >= 0) {

                String ref = getVisualLabelName(labIndex);
                newLine = true;
                sb.append(ref + ": ");
            }
        }

        if (block != null) {
            String ref = block.getSourceRefText(pc, refIterator);
            if (ref != null) {
                sb.append("; " + ref.replace('\n',' '));
                newLine = true;
            }
        }
        if (newLine) {
            sb.append("\n");
            if (printOffsets)
                sb.append(Utils.align("", ' ', 8, false));
        }
    }

    String getOffset(int off) {
        if (hexaOffsets)
            return "0x"+Integer.toHexString(off);
        else
            return Integer.toString(off);
    }

    void printOffset() {
        String pcs = getOffset(pc);
        if (findCodeOffset) {
            if (codeOffset<0)
                sb.append(Utils.align("" + pcs + ":", ' ', 12, false));
            else {
                String cods = getOffset(pc-codeOffset);
                sb.append(Utils.align("" + pcs + "  (" + cods+
                        "):", ' ', 12, false));
            }
        }
        else
            sb.append(Utils.align("" + pcs + ":", ' ', 8, false));
    }
    // The most reliable way to identify the code entry point is by trying to find the first
    // RETURN. Other patterns, such as, CODECOPY PUSH 0 RETURN STOP, do not always work
    // Note that the 0 of push 0 is not checked here, but in other place
    // Note that STOP is assembled as .data 0:
    // It is not actually an opcode to be executed, because it doesn't have a JUMPPDEST
    static byte endOfHeader[] = {0x39 ,0x60 ,(byte) 0xf3, 0x00};
    public String dissasembleInternal() {
        ops = nullToEmpty(code);
        precompile();
        sb = new StringBuilder();
        refIterator = new int[1];
        tagIterator= new int[1];

        while (!isStopped()) {
            if (printOffsets)
                printOffset();

            printCodeSections();
            byte opCode = getOp(pc);
            //
            if (codeOffset<0)
                if (endOfHeader[codeOffsetState]==opCode) {
                    codeOffsetState++;
                    if (codeOffsetState==endOfHeader.length)
                        codeOffset = pc+1;
                } else
                    codeOffsetState=0;

            OpCode op = OpCode.code(opCode);

            if (op == null) {
                sb.append("<UNKNOWN>: ").append(0xFF & opCode).append("\n");
                pc++;
                continue;
            }


            if (op.name().startsWith("PUSH")) {
                BigInteger v =printPush(op);
                if (codeOffset<0)
                 if ((v!=null) && (codeOffsetState>0))
                    if (v.bitCount()!=0)
                        codeOffsetState=0;

                int nPush = op.val() - OpCode.PUSH1.val() + 1;
                setPC(pc + nPush + 1);
            } else {
                sb.append(' ').append(op.name());
                setPC(pc+1);
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}

