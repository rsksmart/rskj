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
 * The fundamental network cost unit. Paid for exclusively by Ether, which is converted
 * freely to and from Gas as required. Gas does not exist outside of the internal Ethereum
 * computation engine; its price is set by the Transaction and miners are free to
 * ignore Transactions whose Gas price is too low.
 */
public class GasCost {

    /* backwards compatibility, remove eventually */
    public final static int STEP = 1;
    public final static int SSTORE = 300;
    /* backwards compatibility, remove eventually */

    public final static int ZEROSTEP = 0;
    public final static int QUICKSTEP = 2;
    public final static int FASTESTSTEP = 3;
    public final static int FASTSTEP = 5;
    public final static int MIDSTEP = 8;
    public final static int SLOWSTEP = 10;
    public final static int EXTSTEP = 20;

    public final static int GENESISGASLIMIT = 1000000;
    public final static int MINGASLIMIT = 125000;

    public final static int BALANCE = 400;
    public final static int SHA3 = 30;
    public final static int SHA3_WORD = 6;
    public final static int SLOAD = 200;
    public final static int STOP = 0;
    public final static int SUICIDE = 5000;
    public final static int CODEREPLACE = 15000;
    public final static int CLEAR_SSTORE = 5000;
    public final static int SET_SSTORE = 20000;
    public final static int RESET_SSTORE = 5000;
    public final static int REFUND_SSTORE = 15000;
    public final static int CREATE = 32000;

    public final static int JUMPDEST = 1;
    public final static int CREATE_DATA_BYTE = 5;
    public final static int CALL = 700;
    public final static int STIPEND_CALL = 2300; // For transferring coins in CALL, this is always passed to child
    public final static int VT_CALL = 9000;  //value transfer call
    public final static int NEW_ACCT_CALL = 25000;  //new account call
    public final static int MEMORY = 3; // TODO: Memory in V0 is more expensive than V1: This MUST be modified before release
    public final static int MEMORY_V1 =3;
    public final static int SUICIDE_REFUND = 24000;
    public final static int QUAD_COEFF_DIV = 512;
    public final static int CREATE_DATA = 200; // paid for each new byte of code
    public final static int REPLACE_DATA = 50; // paid for each byte of code replaced
    public final static int TX_NO_ZERO_DATA = 68;
    public final static int TX_ZERO_DATA = 4;
    public final static int TRANSACTION = 21000;
    public final static int TRANSACTION_DEFAULT = 90000; //compatibility with ethereum (mmarquez)
    public final static int TRANSACTION_CREATE_CONTRACT = 53000;
    public final static int LOG_GAS = 375;
    public final static int LOG_DATA_GAS = 8;
    public final static int LOG_TOPIC_GAS = 375;
    public final static int COPY_GAS = 3;
    public final static int EXP_GAS = 10;
    public final static int EXP_BYTE_GAS = 10;
    public final static int IDENTITY = 15;
    public final static int IDENTITY_WORD = 3;
    public final static int RIPEMD160 = 600;
    public final static int RIPEMD160_WORD = 120;
    public final static int SHA256 = 60;
    public final static int SHA256_WORD = 12;
    public final static int EC_RECOVER = 3000;
    public final static int EXT_CODE_SIZE = 700;
    public final static int EXT_CODE_COPY = 700;
    public final static int NEW_ACCT_SUICIDE = 25000;
    public final static int RETURN = 0;
}
