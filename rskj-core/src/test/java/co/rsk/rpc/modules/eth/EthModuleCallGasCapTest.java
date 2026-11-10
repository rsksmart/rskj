/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.TestUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.util.TransactionFactoryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * eth_call runs with at most rpc.callGasCap, whatever gas the caller asks for.
 */
class EthModuleCallGasCapTest {

    private static final long CALL_GAS_CAP = 10_000_000L;

    /** GAS PUSH1 0 MSTORE PUSH1 0x20 PUSH1 0 RETURN: returns the gas left after GAS as a 32-byte word. */
    private static final String GAS_LEFT_CONTRACT = "0x5a60005260206000f3";

    /**
     * Cap minus the 21,000 intrinsic cost of a call with no data, minus the 2 gas GAS itself costs
     * before it reads the remaining gas: 10,000,000 - 21,000 - 2 = 9,978,998 = 0x984476.
     */
    private static final String GAS_LEFT_AT_CAP = "0x" + "0".repeat(58) + "984476";

    @TempDir
    public Path tempDir;

    private EthModule ethModule;
    private RskAddress gasLeftContract;

    @BeforeEach
    void setUp() {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("rpc.callGasCap", ConfigValueFactory.fromAnyRef(CALL_GAS_CAP)));
        RskTestFactory factory = new RskTestFactory(tempDir, config);
        gasLeftContract = new ContractRunner(factory).addContract(GAS_LEFT_CONTRACT);
        ethModule = factory.getEthModule();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0x989681",                 // cap + 1
            "0x7fffffffffffffff",       // 2^63 - 1
            "0x8000000000000000",       // 2^63
            "0xffffffffffffffff",       // 2^64 - 1
            "0x10000000000000000",      // 2^64, nine bytes
    })
    void call_gasAboveCap_runsWithTheCap(String gas) {
        CallArguments args = callArgs(gas);

        String result = ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));

        assertEquals(GAS_LEFT_AT_CAP, result);
    }

    @Test
    void call_zeroPaddedGas_isReadByValue() {
        CallArguments args = callArgs("0x000000000000000001");

        RskJsonRpcRequestException error = assertThrows(RskJsonRpcRequestException.class,
                () -> ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest")));

        assertEquals(-32010, error.getCode());
        assertEquals("Not enough gas for transaction execution: tx needs: 21000 tx sent: 1", error.getMessage());
    }

    private CallArguments callArgs(String gas) {
        CallArguments args = new CallArguments();
        args.setFrom("0x" + TestUtils.generateAddress("from").toHexString());
        args.setTo("0x" + gasLeftContract.toHexString());
        args.setGas(gas);
        args.setGasPrice("0x0");
        args.setValue("0x0");
        return args;
    }
}
