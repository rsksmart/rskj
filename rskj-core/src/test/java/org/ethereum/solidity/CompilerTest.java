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

package org.ethereum.solidity;

import org.ethereum.config.SystemProperties;
import org.ethereum.solidity.compiler.CompilationResult;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * Created by Anton Nashatyrev on 03.03.2016.
 */
class CompilerTest {

    @Test
    @Disabled("???")
    void simpleTest() throws IOException {
        SystemProperties systemProperties = Mockito.mock(SystemProperties.class);
        String solc = System.getProperty("solc");
        if (solc == null || solc.isEmpty())
            solc = "/usr/bin/solc";

        Mockito.when(systemProperties.customSolcPath()).thenReturn(solc);

        SolidityCompiler solidityCompiler = new SolidityCompiler(systemProperties);

        String contract =
            "contract a {" +
                    "  int i1;" +
                    "  function i() returns (int) {" +
                    "    return i1;" +
                    "  }" +
                    "}";
        SolidityCompiler.Result res = solidityCompiler.compile(
                contract.getBytes(), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN, SolidityCompiler.Options.INTERFACE);
        System.out.println("Out: '" + res.output + "'");
        System.out.println("Err: '" + res.errors + "'");
        CompilationResult result = CompilationResult.parse(res.output);
        CompilationResult.ContractMetadata cmeta = result.contracts.get("a");

        if (cmeta == null)
            cmeta = result.contracts.get("<stdin>:a");

        if (cmeta != null)
            System.out.println(cmeta.bin);
        else
            Assertions.fail();
    }

    public static void main(String[] args) throws Exception {
        new CompilerTest().simpleTest();
    }
}
