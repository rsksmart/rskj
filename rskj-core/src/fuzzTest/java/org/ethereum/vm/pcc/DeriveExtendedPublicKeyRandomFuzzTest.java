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

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.bto.DeriveExtendedPublicKey;
import co.rsk.pcc.bto.HDWalletUtilsHelper;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Tag;

class DeriveExtendedPublicKeyRandomFuzzTest {
    private DeriveExtendedPublicKey method;

    @BeforeEach
    void createMethod() {
        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        HDWalletUtilsHelper helper = new HDWalletUtilsHelper();
        method = new DeriveExtendedPublicKey(executionEnvironment, helper);
    }

    @Tag("DeriveExtendedPublicKeyRandomFuzzExecuteRandom")
    @FuzzTest
    void executes(FuzzedDataProvider data) throws NativeContractIllegalArgumentException {
        String arg1 = data.consumeString(120000);
        String arg2 = data.consumeString(120000);
        try {
            method.execute(new Object[]{
                    arg1,
                    arg2
            });
        } catch (NativeContractIllegalArgumentException e) {}
    }
}
