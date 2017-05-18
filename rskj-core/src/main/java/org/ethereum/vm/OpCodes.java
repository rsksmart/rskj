/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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


package org.ethereum.vm;

/**
 * Created by Sergio on 07/07/2016.
 */
public class OpCodes {
    /**
     * Halts execution (0x00)
     */
    static final byte opSTOP=0x00;

    /*  Arithmetic Operations   */

    /**
     * (0x01) Addition operation
     */
    static final byte opADD=0x01 ;
    /**
     * (0x02) Multiplication operation
     */
    static final byte opMUL=0x02 ;
    /**
     * (0x03) Subtraction operations
     */
    static final byte opSUB=0x03 ;
    /**
     * (0x04) Integer division operation
     */
    static final byte opDIV=0x04 ;
    /**
     * (0x05) Signed integer division operation
     */
    static final byte opSDIV=0x05 ;
    /**
     * (0x06) Modulo remainder operation
     */
    static final byte opMOD=0x06 ;
    /**
     * (0x07) Signed modulo remainder operation
     */
    static final byte opSMOD=0x07 ;
    /**
     * (0x08) Addition combined with modulo
     * remainder operation
     */
    static final byte opADDMOD=0x08  ;
    /**
     * (0x09) Multiplication combined with modulo
     * remainder operation
     */
    static final byte opMULMOD=0x09  ;
    /**
     * (0x0a) Exponential operation
     */
    static final byte opEXP=0x0a ;
    /**
     * (0x0b) end length of signed integer
     */
    static final byte opSIGNEXTEND=0x0b ;

    /*  Bitwise Logic & Comparison Operations   */

    /**
     * (0x10) Less-than comparison
     */
    static final byte opLT=0X10 ;
    /**
     * (0x11) Greater-than comparison
     */
    static final byte opGT=0X11 ;
    /**
     * (0x12) Signed less-than comparison
     */
    static final byte opSLT=0X12 ;
    /**
     * (0x13) Signed greater-than comparison
     */
    static final byte opSGT=0X13 ;
    /**
     * (0x14) Equality comparison
     */
    static final byte opEQ=0X14 ;
    /**
     * (0x15) Negation operation
     */
    static final byte opISZERO=0x15 ;
    /**
     * (0x16) Bitwise AND operation
     */
    static final byte opAND=0x16 ;
    /**
     * (0x17) Bitwise OR operation
     */
    static final byte opOR=0x17 ;
    /**
     * (0x18) Bitwise XOR operation
     */
    static final byte opXOR=0x18 ;
    /**
     * (0x19) Bitwise NOT operationr
     */
    static final byte opNOT=0x19 ;
    /**
     * (0x1a) Retrieve single byte from word
     */
    static final byte opBYTE=0x1a ;

    /*  Cryptographic Operations    */

    /**
     * (0x20) Compute SHA3-256 hash
     */
    static final byte opSHA3=0x20 ;

    /*  Environmental Information   */

    /**
     * (0x30)  Get address of currently
     * executing account
     */
    static final byte opADDRESS=0x30 ;
    /**
     * (0x31) Get balance of the given account
     */
    static final byte opBALANCE=0x31 ;
    /**
     * (0x32) Get execution origination address
     */
    static final byte opORIGIN=0x32 ;
    /**
     * (0x33) Get caller address
     */
    static final byte opCALLER=0x33 ;
    /**
     * (0x34) Get deposited value by the
     * instruction/transaction responsible
     * for this execution
     */
    static final byte opCALLVALUE=0x34 ;
    /**
     * (0x35) Get input data of current
     * environment
     */
    static final byte opCALLDATALOAD=0x35 ;
    /**
     * (0x36) Get size of input data in current
     * environment
     */
    static final byte opCALLDATASIZE=0x36 ;
    /**
     * (0x37) Copy input data in current
     * environment to memory
     */
    static final byte opCALLDATACOPY=0x37 ;
    /**
     * (0x38) Get size of code running in
     * current environment
     */
    static final byte opCODESIZE=0x38 ;
    /**
     * (0x39) Copy code running in current
     * environment to memory
     */
    static final byte opCODECOPY=0x39 ; // [len code_start mem_start CODECOPY]
    /**
     * (0x3a) Get price of gas in current
     * environment
     */
    static final byte opGASPRICE=0x3a ;
    /**
     * (0x3b) Get size of code running in
     * current environment with given offset
     */
    static final byte opEXTCODESIZE=0x3b ;
    /**
     * (0x3c) Copy code running in current
     * environment to memory with given offset
     */
    static final byte opEXTCODECOPY=0x3c;

    /*  Block Information   */

    /**
     * (0x40) Get hash of most recent
     * complete block
     */
    static final byte opBLOCKHASH=0x40 ;
    /**
     * (0x41) Get the block’s coin address
     */
    static final byte opCOINBASE=0x41 ;
    /**
     * (x042) Get the block’s timestamp
     */
    static final byte opTIMESTAMP=0x42 ;
    /**
     * (0x43) Get the block’s number
     */
    static final byte opNUMBER=0x43 ;
    /**
     * (0x44) Get the block’s difficulty
     */
    static final byte opDIFFICULTY=0x44 ;
    /**
     * (0x45) Get the block’s gas limit
     */
    static final byte opGASLIMIT=0x45 ;

    /*  Memory Storage and F Operations */

    /**
     * (0x50) Remove item from stack
     */
    static final byte opPOP=0x50 ;
    /**
     * (0x51) Load word from memory
     */
    static final byte opMLOAD=0x51 ;
    /**
     * (0x52) Save word to memory
     */
    static final byte opMSTORE=0x52 ;
    /**
     * (0x53) Save byte to memory
     */
    static final byte opMSTORE8=0x53 ;
    /**
     * (0x54) Load word from storage
     */
    static final byte opSLOAD=0x54 ;
    /**
     * (0x55) Save word to storage
     */
    static final byte opSSTORE=0x55 ;
    /**
     * (0x56) Alter the program counter
     */
    static final byte opJUMP=0x56 ;
    /**
     * (0x57) Conditionally alter the program
     * counter
     */
    static final byte opJUMPI=0x57;
    /**
     * (0x58) Get the program counter
     */
    static final byte opPC=0x58 ;
    /**
     * (0x59) Get the size of active memory
     */
    static final byte opMSIZE=0x59 ;
    /**
     * (0x5a) Get the amount of available gas
     */
    static final byte opGAS=0x5a ;
    /**
     * (0x5b)
     */
    static final byte opJUMPDEST=0x5b ;

    /*  Push Operations */

    /**
     * (0x60) Place 1-byte item on stack
     */
    static final byte opPUSH1=0x60 ;
    /**
     * (0x61) Place 2-byte item on stack
     */
    static final byte opPUSH2=0x61 ;
    /**
     * (0x62) Place 3-byte item on stack
     */
    static final byte opPUSH3=0x62 ;
    /**
     * (0x63) Place 4-byte item on stack
     */
    static final byte opPUSH4=0x63 ;
    /**
     * (0x64) Place 5-byte item on stack
     */
    static final byte opPUSH5=0x64 ;
    /**
     * (0x65) Place 6-byte item on stack
     */
    static final byte opPUSH6=0x65 ;
    /**
     * (0x66) Place 7-byte item on stack
     */
    static final byte opPUSH7=0x66 ;
    /**
     * (0x67) Place 8-byte item on stack
     */
    static final byte opPUSH8=0x67 ;
    /**
     * (0x68) Place 9-byte item on stack
     */
    static final byte opPUSH9=0x68 ;
    /**
     * (0x69) Place 10-byte item on stack
     */
    static final byte opPUSH10=0x69 ;
    /**
     * (0x6a) Place 11-byte item on stack
     */
    static final byte opPUSH11=0x6a ;
    /**
     * (0x6b) Place 12-byte item on stack
     */
    static final byte opPUSH12=0x6b ;
    /**
     * (0x6c) Place 13-byte item on stack
     */
    static final byte opPUSH13=0x6c ;
    /**
     * (0x6d) Place 14-byte item on stack
     */
    static final byte opPUSH14=0x6d ;
    /**
     * (0x6e) Place 15-byte item on stack
     */
    static final byte opPUSH15=0x6e ;
    /**
     * (0x6f) Place 16-byte item on stack
     */
    static final byte opPUSH16=0x6f ;
    /**
     * (0x70) Place 17-byte item on stack
     */
    static final byte opPUSH17=0x70 ;
    /**
     * (0x71) Place 18-byte item on stack
     */
    static final byte opPUSH18=0x71 ;
    /**
     * (0x72) Place 19-byte item on stack
     */
    static final byte opPUSH19=0x72 ;
    /**
     * (0x73) Place 20-byte item on stack
     */
    static final byte opPUSH20=0x73 ;
    /**
     * (0x74) Place 21-byte item on stack
     */
    static final byte opPUSH21=0x74 ;
    /**
     * (0x75) Place 22-byte item on stack
     */
    static final byte opPUSH22=0x75 ;
    /**
     * (0x76) Place 23-byte item on stack
     */
    static final byte opPUSH23=0x76 ;
    /**
     * (0x77) Place 24-byte item on stack
     */
    static final byte opPUSH24=0x77 ;
    /**
     * (0x78) Place 25-byte item on stack
     */
    static final byte opPUSH25=0x78 ;
    /**
     * (0x79) Place 26-byte item on stack
     */
    static final byte opPUSH26=0x79 ;
    /**
     * (0x7a) Place 27-byte item on stack
     */
    static final byte opPUSH27=0x7a ;
    /**
     * (0x7b) Place 28-byte item on stack
     */
    static final byte opPUSH28=0x7b ;
    /**
     * (0x7c) Place 29-byte item on stack
     */
    static final byte opPUSH29=0x7c ;
    /**
     * (0x7d) Place 30-byte item on stack
     */
    static final byte opPUSH30=0x7d ;
    /**
     * (0x7e) Place 31-byte item on stack
     */
    static final byte opPUSH31=0x7e ;
    /**
     * (0x7f) Place 32-byte (full word)
     * item on stack
     */
    static final byte opPUSH32=0x7f ;

    /*  Duplicate Nth item from the stack   */

    /**
     * (0x80) Duplicate 1st item on stack
     */
    static final byte opDUP1=(byte)0x80 ;
    /**
     * (0x81) Duplicate 2nd item on stack
     */
    static final byte opDUP2=(byte)0x81  ;
    /**
     * (0x82) Duplicate 3rd item on stack
     */
    static final byte opDUP3=(byte)0x82 ;
    /**
     * (0x83) Duplicate 4th item on stack
     */
    static final byte opDUP4=(byte)0x83  ;
    /**
     * (0x84) Duplicate 5th item on stack
     */
    static final byte opDUP5=(byte)0x84 ;
    /**
     * (0x85) Duplicate 6th item on stack
     */
    static final byte opDUP6=(byte)0x85  ;
    /**
     * (0x86) Duplicate 7th item on stack
     */
    static final byte opDUP7=(byte)0x86  ;
    /**
     * (0x87) Duplicate 8th item on stack
     */
    static final byte opDUP8=(byte)0x87  ;
    /**
     * (0x88) Duplicate 9th item on stack
     */
    static final byte opDUP9=(byte)0x88 ;
    /**
     * (0x89) Duplicate 10th item on stack
     */
    static final byte opDUP10=(byte)0x89 ;
    /**
     * (0x8a) Duplicate 11th item on stack
     */
    static final byte opDUP11=(byte)0x8a ;
    /**
     * (0x8b) Duplicate 12th item on stack
     */
    static final byte opDUP12=(byte)0x8b ;
    /**
     * (0x8c) Duplicate 13th item on stack
     */
    static final byte opDUP13=(byte)0x8c ;
    /**
     * (0x8d) Duplicate 14th item on stack
     */
    static final byte opDUP14=(byte)0x8d  ;
    /**
     * (0x8e) Duplicate 15th item on stack
     */
    static final byte opDUP15=(byte)0x8e  ;
    /**
     * (0x8f) Duplicate 16th item on stack
     */
    static final byte opDUP16=(byte)0x8f  ;

    /*  Swap the Nth item from the stack with the top   */

    /**
     * (0x90) Exchange 2nd item from stack with the top
     */
    static final byte opSWAP1=(byte)0x90  ;
    /**
     * (0x91) Exchange 3rd item from stack with the top
     */
    static final byte opSWAP2=(byte)0x91  ;
    /**
     * (0x92) Exchange 4th item from stack with the top
     */
    static final byte opSWAP3=(byte)0x92  ;
    /**
     * (0x93) Exchange 5th item from stack with the top
     */
    static final byte opSWAP4=(byte)0x93  ;
    /**
     * (0x94) Exchange 6th item from stack with the top
     */
    static final byte opSWAP5=(byte)0x94  ;
    /**
     * (0x95) Exchange 7th item from stack with the top
     */
    static final byte opSWAP6=(byte)0x95  ;
    /**
     * (0x96) Exchange 8th item from stack with the top
     */
    static final byte opSWAP7=(byte)0x96  ;
    /**
     * (0x97) Exchange 9th item from stack with the top
     */
    static final byte opSWAP8=(byte)0x97  ;
    /**
     * (0x98) Exchange 10th item from stack with the top
     */
    static final byte opSWAP9=(byte)0x98  ;
    /**
     * (0x99) Exchange 11th item from stack with the top
     */
    static final byte opSWAP10=(byte)0x99;
    /**
     * (0x9a) Exchange 12th item from stack with the top
     */
    static final byte opSWAP11=(byte)0x9a  ;
    /**
     * (0x9b) Exchange 13th item from stack with the top
     */
    static final byte opSWAP12=(byte)0x9b  ;
    /**
     * (0x9c) Exchange 14th item from stack with the top
     */
    static final byte opSWAP13=(byte)0x9c  ;
    /**
     * (0x9d) Exchange 15th item from stack with the top
     */
    static final byte opSWAP14=(byte)0x9d  ;
    /**
     * (0x9e) Exchange 16th item from stack with the top
     */
    static final byte opSWAP15=(byte)0x9e ;
    /**
     * (0x9f) Exchange 17th item from stack with the top
     */
    static final byte opSWAP16=(byte)0x9f  ;

    /**
     * (0xa[n]) log some data for some addres with 0..n tags [addr [tag0..tagn] data]
     */
    static final byte opLOG0=(byte)0xa0 ;
    static final byte opLOG1=(byte)0xa1 ;
    static final byte opLOG2=(byte)0xa2  ;
    static final byte opLOG3=(byte)0xa3 ;
    static final byte opLOG4=(byte)0xa4  ;

    /*  System operations   */
    static final byte opCODEREPLACE=(byte)0xa8  ;

    /**
     * (0xf0) Create a new account with associated code
     */
    static final byte opCREATE=(byte)0xf0  ;   //       [in_size] [in_offs] [gas_val] CREATE
    /**
     * (cxf1) Message-call into an account
     */
    static final byte opCALL=(byte)0xf1  ;     //       [out_data_size] [out_data_start] [in_data_size] [in_data_start] [value] [to_addr]
    // [gas] CALL
    /**
     * (0xf2) Calls self but grabbing the code from the
     * TO argument instead of from one's own address
     */
    static final byte opCALLCODE=(byte)0xf2  ;
    /**
     * (0xf3) Halt execution returning output data
     */
    static final byte opRETURN=(byte)0xf3 ;

    /**
     * (0xf4)  similar in idea to CALLCODE except that it propagates the sender and value
     *  from the parent scope to the child scope ie. the call created has the same sender
     *  and value as the original call.
     *  also the Value parameter is omitted for this opCode
     */
    static final byte opDELEGATECALL=(byte)0xf4 ;
    static final byte opHEADER=(byte)0xfc  ;
    /**
     * (0xff) Halt execution and register account for
     * later deletion
     */
    static final byte opSUICIDE=(byte)0xff;
}
