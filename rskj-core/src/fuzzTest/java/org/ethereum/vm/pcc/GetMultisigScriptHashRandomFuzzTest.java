/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package org.ethereum.vm.pcc;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.bto.GetMultisigScriptHash;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Tag;

class GetMultisigScriptHashRandomFuzzTest {
    private GetMultisigScriptHash method;

    @BeforeEach
    void createMethod() {
        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        method = new GetMultisigScriptHash(executionEnvironment);
    }

    @Tag("GetMultisigScriptHashRandomFuzzExecuteMixed")
    @FuzzTest
    void executesWithMixed(FuzzedDataProvider data) throws NativeContractIllegalArgumentException {
        byte[] arg1 = data.consumeBytes(1200000);
        byte[] arg2 = data.consumeBytes(1200000);
        byte[] arg3 = data.consumeBytes(1200000);
        byte[] arg4 = data.consumeBytes(1200000);
        byte[] arg5 = data.consumeBytes(1200000);
        byte[] arg6 = data.consumeBytes(1200000);
        byte[] arg7 = data.consumeBytes(1200000);
        byte[] arg8 = data.consumeBytes(1200000);
        byte[] arg9 = data.consumeBytes(1200000);

        try {
            method.execute(new Object[]{
                    BigInteger.valueOf(8L),
                    new byte[][]{
                            arg1,
                            arg2,
                            arg3,
                            arg4,
                            arg5,
                            arg6,
                            arg7,
                            arg8,
                            arg9,
                    }
            });
        } catch (NativeContractIllegalArgumentException e) {}
    }

}
