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

    private OpCodes() {

    }

    /**
     * Halts execution (0x00)
     */
    static final byte OP_STOP =0x00;

    /*  Arithmetic Operations   */

    /**
     * (0x01) Addition operation
     */
    static final byte OP_ADD =0x01 ;
    /**
     * (0x02) Multiplication operation
     */
    static final byte OP_MUL =0x02 ;
    /**
     * (0x03) Subtraction operations
     */
    static final byte OP_SUB =0x03 ;
    /**
     * (0x04) Integer division operation
     */
    static final byte OP_DIV =0x04 ;
    /**
     * (0x05) Signed integer division operation
     */
    static final byte OP_SDIV =0x05 ;
    /**
     * (0x06) Modulo remainder operation
     */
    static final byte OP_MOD =0x06 ;
    /**
     * (0x07) Signed modulo remainder operation
     */
    static final byte OP_SMOD =0x07 ;
    /**
     * (0x08) Addition combined with modulo
     * remainder operation
     */
    static final byte OP_ADDMOD =0x08  ;
    /**
     * (0x09) Multiplication combined with modulo
     * remainder operation
     */
    static final byte OP_MULMOD =0x09  ;
    /**
     * (0x0a) Exponential operation
     */
    static final byte OP_EXP =0x0a ;
    /**
     * (0x0b) end length of signed integer
     */
    static final byte OP_SIGNEXTEND =0x0b ;

    /*  Bitwise Logic & Comparison Operations   */

    /**
     * (0x10) Less-than comparison
     */
    static final byte OP_LT =0X10 ;
    /**
     * (0x11) Greater-than comparison
     */
    static final byte OP_GT =0X11 ;
    /**
     * (0x12) Signed less-than comparison
     */
    static final byte OP_SLT =0X12 ;
    /**
     * (0x13) Signed greater-than comparison
     */
    static final byte OP_SGT =0X13 ;
    /**
     * (0x14) Equality comparison
     */
    static final byte OP_EQ =0X14 ;
    /**
     * (0x15) Negation operation
     */
    static final byte OP_ISZERO =0x15 ;
    /**
     * (0x16) Bitwise AND operation
     */
    static final byte OP_AND =0x16 ;
    /**
     * (0x17) Bitwise OR operation
     */
    static final byte OP_OR =0x17 ;
    /**
     * (0x18) Bitwise XOR operation
     */
    static final byte OP_XOR =0x18 ;
    /**
     * (0x19) Bitwise NOT operationr
     */
    static final byte OP_NOT =0x19 ;
    /**
     * (0x1a) Retrieve single byte from word
     */
    static final byte OP_BYTE =0x1a ;

    /**
     * (0x1b) Bitwise SHIFT LEFT operation
     */
    public static final byte OP_SHL =0x1b ;
    /**
     * (0x1c) Bitwise SHIFT RIGHT operation
     */
    public static final byte OP_SHR =0x1c ;
    /**
     * (0x1d) Bitwise ARITHMETIC SHIFT RIGHT operation
     */
    public static final byte OP_SAR =0x1d ;

    /*  Cryptographic Operations    */

    /**
     * (0x20) Compute SHA3-256 hash
     */
    static final byte OP_SHA_3 =0x20 ;

    /*  Environmental Information   */

    /**
     * (0x30)  Get address of currently
     * executing account
     */
    static final byte OP_ADDRESS =0x30 ;
    /**
     * (0x31) Get balance of the given account
     */
    static final byte OP_BALANCE =0x31 ;
    /**
     * (0x32) Get execution origination address
     */
    static final byte OP_ORIGIN =0x32 ;
    /**
     * (0x33) Get caller address
     */
    static final byte OP_CALLER =0x33 ;
    /**
     * (0x34) Get deposited value by the
     * instruction/transaction responsible
     * for this execution
     */
    static final byte OP_CALLVALUE =0x34 ;
    /**
     * (0x35) Get input data of current
     * environment
     */
    static final byte OP_CALLDATALOAD =0x35 ;
    /**
     * (0x36) Get size of input data in current
     * environment
     */
    static final byte OP_CALLDATASIZE =0x36 ;
    /**
     * (0x37) Copy input data in current
     * environment to memory
     */
    static final byte OP_CALLDATACOPY =0x37 ;
    /**
     * (0x38) Get size of code running in
     * current environment
     */
    static final byte OP_CODESIZE =0x38 ;
    /**
     * (0x39) Copy code running in current
     * environment to memory
     */
    static final byte OP_CODECOPY =0x39 ; // [len code_start mem_start CODECOPY]
    /**
     * (0x3a) Get price of gas in current
     * environment
     */
    static final byte OP_GASPRICE =0x3a ;
    /**
     * (0x3b) Get size of code running in
     * current environment with given offset
     */
    static final byte OP_EXTCODESIZE =0x3b ;
    /**
     * (0x3c) Copy code running in current
     * environment to memory with given offset
     */
    static final byte OP_EXTCODECOPY =0x3c;
    /**
     * (0x3d and 0x3e) A mechanism to allow
     * returning arbitrary-length data.
     * After a call, return data is kept inside
     * a virtual buffer from which the caller
     * can copy it (or parts thereof) into
     * memory. At the next call, the buffer is
     * overwritten.
     */
    static final byte OP_RETURNDATASIZE = 0x3d;
    static final byte OP_RETURNDATACOPY = 0x3e;

    /**
     * (0x3f) Get hash of code running in current
     * environment
     */
    public static final byte OP_EXTCODEHASH = 0x3f;

    /*  Block Information   */

    /**
     * (0x40) Get hash of most recent
     * complete block
     */
    static final byte OP_BLOCKHASH =0x40 ;
    /**
     * (0x41) Get the block’s coin address
     */
    static final byte OP_COINBASE =0x41 ;
    /**
     * (x042) Get the block’s timestamp
     */
    static final byte OP_TIMESTAMP =0x42 ;
    /**
     * (0x43) Get the block’s number
     */
    static final byte OP_NUMBER =0x43 ;
    /**
     * (0x44) Get the block’s difficulty
     */
    static final byte OP_DIFFICULTY =0x44 ;
    /**
     * (0x45) Get the block’s gas limit
     */
    static final byte OP_GASLIMIT =0x45 ;
    /**
     * (0x46) Get the chain id
     */
    public static final byte OP_CHAINID =0x46 ;
    /**
     * (0x45) Get the senders balance
     */
    public static final byte OP_SELFBALANCE = 0x47 ;

    /*  Memory Storage and F Operations */

    /**
     * (0x50) Remove item from stack
     */
    static final byte OP_POP =0x50 ;
    /**
     * (0x51) Load word from memory
     */
    static final byte OP_MLOAD =0x51 ;
    /**
     * (0x52) Save word to memory
     */
    static final byte OP_MSTORE =0x52 ;
    /**
     * (0x53) Save byte to memory
     */
    static final byte OP_MSTORE_8 =0x53 ;
    /**
     * (0x54) Load word from storage
     */
    static final byte OP_SLOAD =0x54 ;
    /**
     * (0x55) Save word to storage
     */
    static final byte OP_SSTORE =0x55 ;
    /**
     * (0x56) Alter the program counter
     */
    static final byte OP_JUMP =0x56 ;
    /**
     * (0x57) Conditionally alter the program
     * counter
     */
    static final byte OP_JUMPI =0x57;
    /**
     * (0x58) Get the program counter
     */
    static final byte OP_PC =0x58 ;
    /**
     * (0x59) Get the size of active memory
     */
    static final byte OP_MSIZE =0x59 ;
    /**
     * (0x5a) Get the amount of available gas
     */
    static final byte OP_GAS =0x5a ;
    /**
     * (0x5b)
     */
    static final byte OP_JUMPDEST =0x5b ;

    /*  Subroutines Operations */

    /**
     * (0x5c)
     */
    public static final byte OP_BEGINSUB =0x5c ;
    /**
     * (0x5d)
     */
    public static final byte OP_RETURNSUB =0x5d ;
    /**
     * (0x5e)
     */
    public static final byte OP_JUMPSUB =0x5e ;

    /*  Push Operations */

    /**
     * (0x60) Place 1-byte item on stack
     */
    static final byte OP_PUSH_1 =0x60 ;
    /**
     * (0x61) Place 2-byte item on stack
     */
    static final byte OP_PUSH_2 =0x61 ;
    /**
     * (0x62) Place 3-byte item on stack
     */
    static final byte OP_PUSH_3 =0x62 ;
    /**
     * (0x63) Place 4-byte item on stack
     */
    static final byte OP_PUSH_4 =0x63 ;
    /**
     * (0x64) Place 5-byte item on stack
     */
    static final byte OP_PUSH_5 =0x64 ;
    /**
     * (0x65) Place 6-byte item on stack
     */
    static final byte OP_PUSH_6 =0x65 ;
    /**
     * (0x66) Place 7-byte item on stack
     */
    static final byte OP_PUSH_7 =0x66 ;
    /**
     * (0x67) Place 8-byte item on stack
     */
    static final byte OP_PUSH_8 =0x67 ;
    /**
     * (0x68) Place 9-byte item on stack
     */
    static final byte OP_PUSH_9 =0x68 ;
    /**
     * (0x69) Place 10-byte item on stack
     */
    static final byte OP_PUSH_10 =0x69 ;
    /**
     * (0x6a) Place 11-byte item on stack
     */
    static final byte OP_PUSH_11 =0x6a ;
    /**
     * (0x6b) Place 12-byte item on stack
     */
    static final byte OP_PUSH_12 =0x6b ;
    /**
     * (0x6c) Place 13-byte item on stack
     */
    static final byte OP_PUSH_13 =0x6c ;
    /**
     * (0x6d) Place 14-byte item on stack
     */
    static final byte OP_PUSH_14 =0x6d ;
    /**
     * (0x6e) Place 15-byte item on stack
     */
    static final byte OP_PUSH_15 =0x6e ;
    /**
     * (0x6f) Place 16-byte item on stack
     */
    static final byte OP_PUSH_16 =0x6f ;
    /**
     * (0x70) Place 17-byte item on stack
     */
    static final byte OP_PUSH_17 =0x70 ;
    /**
     * (0x71) Place 18-byte item on stack
     */
    static final byte OP_PUSH_18 =0x71 ;
    /**
     * (0x72) Place 19-byte item on stack
     */
    static final byte OP_PUSH_19 =0x72 ;
    /**
     * (0x73) Place 20-byte item on stack
     */
    static final byte OP_PUSH_20 =0x73 ;
    /**
     * (0x74) Place 21-byte item on stack
     */
    static final byte OP_PUSH_21 =0x74 ;
    /**
     * (0x75) Place 22-byte item on stack
     */
    static final byte OP_PUSH_22 =0x75 ;
    /**
     * (0x76) Place 23-byte item on stack
     */
    static final byte OP_PUSH_23 =0x76 ;
    /**
     * (0x77) Place 24-byte item on stack
     */
    static final byte OP_PUSH_24 =0x77 ;
    /**
     * (0x78) Place 25-byte item on stack
     */
    static final byte OP_PUSH_25 =0x78 ;
    /**
     * (0x79) Place 26-byte item on stack
     */
    static final byte OP_PUSH_26 =0x79 ;
    /**
     * (0x7a) Place 27-byte item on stack
     */
    static final byte OP_PUSH_27 =0x7a ;
    /**
     * (0x7b) Place 28-byte item on stack
     */
    static final byte OP_PUSH_28 =0x7b ;
    /**
     * (0x7c) Place 29-byte item on stack
     */
    static final byte OP_PUSH_29 =0x7c ;
    /**
     * (0x7d) Place 30-byte item on stack
     */
    static final byte OP_PUSH_30 =0x7d ;
    /**
     * (0x7e) Place 31-byte item on stack
     */
    static final byte OP_PUSH_31 =0x7e ;
    /**
     * (0x7f) Place 32-byte (full word)
     * item on stack
     */
    static final byte OP_PUSH_32 =0x7f ;

    /*  Duplicate Nth item from the stack   */

    /**
     * (0x80) Duplicate 1st item on stack
     */
    static final byte OP_DUP_1 =(byte)0x80 ;
    /**
     * (0x81) Duplicate 2nd item on stack
     */
    static final byte OP_DUP_2 =(byte)0x81  ;
    /**
     * (0x82) Duplicate 3rd item on stack
     */
    static final byte OP_DUP_3 =(byte)0x82 ;
    /**
     * (0x83) Duplicate 4th item on stack
     */
    static final byte OP_DUP_4 =(byte)0x83  ;
    /**
     * (0x84) Duplicate 5th item on stack
     */
    static final byte OP_DUP_5 =(byte)0x84 ;
    /**
     * (0x85) Duplicate 6th item on stack
     */
    static final byte OP_DUP_6 =(byte)0x85  ;
    /**
     * (0x86) Duplicate 7th item on stack
     */
    static final byte OP_DUP_7 =(byte)0x86  ;
    /**
     * (0x87) Duplicate 8th item on stack
     */
    static final byte OP_DUP_8 =(byte)0x87  ;
    /**
     * (0x88) Duplicate 9th item on stack
     */
    static final byte OP_DUP_9 =(byte)0x88 ;
    /**
     * (0x89) Duplicate 10th item on stack
     */
    static final byte OP_DUP_10 =(byte)0x89 ;
    /**
     * (0x8a) Duplicate 11th item on stack
     */
    static final byte OP_DUP_11 =(byte)0x8a ;
    /**
     * (0x8b) Duplicate 12th item on stack
     */
    static final byte OP_DUP_12 =(byte)0x8b ;
    /**
     * (0x8c) Duplicate 13th item on stack
     */
    static final byte OP_DUP_13 =(byte)0x8c ;
    /**
     * (0x8d) Duplicate 14th item on stack
     */
    static final byte OP_DUP_14 =(byte)0x8d  ;
    /**
     * (0x8e) Duplicate 15th item on stack
     */
    static final byte OP_DUP_15 =(byte)0x8e  ;
    /**
     * (0x8f) Duplicate 16th item on stack
     */
    static final byte OP_DUP_16 =(byte)0x8f  ;

    /*  Swap the Nth item from the stack with the top   */

    /**
     * (0x90) Exchange 2nd item from stack with the top
     */
    static final byte OP_SWAP_1 =(byte)0x90  ;
    /**
     * (0x91) Exchange 3rd item from stack with the top
     */
    static final byte OP_SWAP_2 =(byte)0x91  ;
    /**
     * (0x92) Exchange 4th item from stack with the top
     */
    static final byte OP_SWAP_3 =(byte)0x92  ;
    /**
     * (0x93) Exchange 5th item from stack with the top
     */
    static final byte OP_SWAP_4 =(byte)0x93  ;
    /**
     * (0x94) Exchange 6th item from stack with the top
     */
    static final byte OP_SWAP_5 =(byte)0x94  ;
    /**
     * (0x95) Exchange 7th item from stack with the top
     */
    static final byte OP_SWAP_6 =(byte)0x95  ;
    /**
     * (0x96) Exchange 8th item from stack with the top
     */
    static final byte OP_SWAP_7 =(byte)0x96  ;
    /**
     * (0x97) Exchange 9th item from stack with the top
     */
    static final byte OP_SWAP_8 =(byte)0x97  ;
    /**
     * (0x98) Exchange 10th item from stack with the top
     */
    static final byte OP_SWAP_9 =(byte)0x98  ;
    /**
     * (0x99) Exchange 11th item from stack with the top
     */
    static final byte OP_SWAP_10 =(byte)0x99;
    /**
     * (0x9a) Exchange 12th item from stack with the top
     */
    static final byte OP_SWAP_11 =(byte)0x9a  ;
    /**
     * (0x9b) Exchange 13th item from stack with the top
     */
    static final byte OP_SWAP_12 =(byte)0x9b  ;
    /**
     * (0x9c) Exchange 14th item from stack with the top
     */
    static final byte OP_SWAP_13 =(byte)0x9c  ;
    /**
     * (0x9d) Exchange 15th item from stack with the top
     */
    static final byte OP_SWAP_14 =(byte)0x9d  ;
    /**
     * (0x9e) Exchange 16th item from stack with the top
     */
    static final byte OP_SWAP_15 =(byte)0x9e ;
    /**
     * (0x9f) Exchange 17th item from stack with the top
     */
    static final byte OP_SWAP_16 =(byte)0x9f  ;

    /**
     * (0xa[n]) log some data for some addres with 0..n tags [addr [tag0..tagn] data]
     */
    static final byte OP_LOG_0 =(byte)0xa0 ;
    static final byte OP_LOG_1 =(byte)0xa1 ;
    static final byte OP_LOG_2 =(byte)0xa2  ;
    static final byte OP_LOG_3 =(byte)0xa3 ;
    static final byte OP_LOG_4 =(byte)0xa4  ;

    /*  System operations   */
    static final byte OP_DUPN = (byte)0xa8;
    static final byte OP_SWAPN = (byte)0xa9;
    static final byte OP_TXINDEX = (byte)0xaa;

    /**
     * (0xf0) Create a new account with associated code
     */
    static final byte OP_CREATE =(byte)0xf0  ;   //       [in_size] [in_offs] [gas_val] CREATE
    /**
     * (cxf1) Message-call into an account
     */
    static final byte OP_CALL =(byte)0xf1  ;     //       [out_data_size] [out_data_start] [in_data_size] [in_data_start] [value] [to_addr]
    // [gas] CALL
    /**
     * (0xf2) Calls self but grabbing the code from the
     * TO argument instead of from one's own address
     */
    static final byte OP_CALLCODE =(byte)0xf2  ;
    /**
     * (0xf3) Halt execution returning output data
     */
    static final byte OP_RETURN =(byte)0xf3 ;

    /**
     * (0xf4)  similar in idea to CALLCODE except that it propagates the sender and value
     *  from the parent scope to the child scope ie. the call created has the same sender
     *  and value as the original call.
     *  also the Value parameter is omitted for this opcode
     */
    static final byte OP_DELEGATECALL =(byte)0xf4 ;

    /**
     * (0xf5) Skinny CREATE2, same as CREATE but with deterministic address
     */
    public static final byte OP_CREATE2 =(byte)0xf5;

    /**
     *  opcode that can be used to call another contract (or itself) while disallowing any
     *  modifications to the state during the call (and its subcalls, if present).
     *  Any opcode that attempts to perform such a modification (see below for details)
     *  will result in an exception instead of performing the modification.
     */
    static final byte OP_STATICCALL =(byte)0xfa ;


    static final byte OP_HEADER =(byte)0xfc  ;

    /**
     * (0xfd) The `REVERT` instruction will stop execution, roll back all state changes done so far
     * and provide a pointer to a memory section, which can be interpreted as an error code or message.
     * While doing so, it will not consume all the remaining gas.
     */
    static final byte OP_REVERT = (byte)0xfd;

    /**
     * (0xff) Halt execution and register account for
     * later deletion
     */
    static final byte OP_SUICIDE =(byte)0xff;
}
