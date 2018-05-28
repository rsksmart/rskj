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
 * The fundamental network cost unit. Paid for exclusively by SBTC, which is converted
 * freely to and from Gas as required. Gas does not exist outside of the internal RSK
 * computation engine; its price is set by the Transaction and miners are free to
 * ignore Transactions whose Gas price is too low.
 */
public class GasCost {

    /* backwards compatibility, remove eventually */
    public static final int STEP = 1;
    public static final int SSTORE = 300;
    /* backwards compatibility, remove eventually */

    public static final int ZEROSTEP = 0;
    public static final int QUICKSTEP = 2;
    public static final int FASTESTSTEP = 3;
    public static final int FASTSTEP = 5;
    public static final int MIDSTEP = 8;
    public static final int SLOWSTEP = 10;
    public static final int EXTSTEP = 20;

    public static final int GENESISGASLIMIT = 1000000;
    public static final int MINGASLIMIT = 125000;

    public static final int BALANCE = 400;
    public static final int SHA3 = 30;
    public static final int SHA3_WORD = 6;
    public static final int SLOAD = 200;
    public static final int STOP = 0;
    public static final int SUICIDE = 5000;
    public static final int CODEREPLACE = 15000;
    public static final int CLEAR_SSTORE = 5000;
    public static final int SET_SSTORE = 20000;
    public static final int RESET_SSTORE = 5000;
    public static final int REFUND_SSTORE = 15000;
    public static final int CREATE = 32000;

    public static final int JUMPDEST = 1;
    public static final int CREATE_DATA_BYTE = 5;
    public static final int CALL = 700;
    public static final int STIPEND_CALL = 2300; // For transferring coins in CALL, this is always passed to child
    public static final int VT_CALL = 9000;  //value transfer call
    public static final int NEW_ACCT_CALL = 25000;  //new account call
    public static final int MEMORY = 3; // TODO: Memory in V0 is more expensive than V1: This MUST be modified before release
    public static final int MEMORY_V1 =3;
    public static final int SUICIDE_REFUND = 24000;
    public static final int QUAD_COEFF_DIV = 512;
    public static final int CREATE_DATA = 200; // paid for each new byte of code
    public static final int REPLACE_DATA = 50; // paid for each byte of code replaced
    public static final int TX_NO_ZERO_DATA = 68;
    public static final int TX_ZERO_DATA = 4;
    public static final int TRANSACTION = 21000;
    public static final int TRANSACTION_DEFAULT = 90000; //compatibility with ethereum (mmarquez)
    public static final int TRANSACTION_CREATE_CONTRACT = 53000;
    public static final int LOG_GAS = 375;
    public static final int LOG_DATA_GAS = 8;
    public static final int LOG_TOPIC_GAS = 375;
    public static final int COPY_GAS = 3;
    public static final int EXP_GAS = 10;
    public static final int EXP_BYTE_GAS = 50;
    public static final int IDENTITY = 15;
    public static final int IDENTITY_WORD = 3;
    public static final int RIPEMD160 = 600;
    public static final int RIPEMD160_WORD = 120;
    public static final int SHA256 = 60;
    public static final int SHA256_WORD = 12;
    public static final int EC_RECOVER = 3000;
    public static final int EXT_CODE_SIZE = 700;
    public static final int EXT_CODE_COPY = 700;
    public static final int NEW_ACCT_SUICIDE = 25000;
    public static final int RETURN = 0;
}
