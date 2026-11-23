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

import org.ethereum.TestUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.util.TransactionFactoryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * eth_call and eth_estimateGas answer a transaction error (-32010) for a call the node rejects before running it,
 * as a broadcast transaction would be rejected. Intrinsic gas below uses the RSKIP400 calldata price: 16 gas per
 * non-zero byte. eth_estimateGas always runs with the 6,800,000 gas estimation cap from reference.conf.
 */
class EthModuleLocalCallRejectionTest {

    private static final int TRANSACTION_ERROR = -32010;

    /** GAS PUSH1 0 MSTORE PUSH1 0x20 PUSH1 0 RETURN: returns the gas left after GAS as a 32-byte word. */
    private static final String GAS_LEFT_CONTRACT = "0x5a60005260206000f3";

    private static final String REMASC = "0x0000000000000000000000000000000001000008";

    private static final String BRIDGE = "0x0000000000000000000000000000000001000006";

    private static final String IDENTITY = "0x0000000000000000000000000000000000000004";

    @TempDir
    public Path tempDir;

    private EthModule ethModule;
    private String contract;
    private String eoa;

    @BeforeEach
    void setUp() {
        RskTestFactory factory = new RskTestFactory(tempDir);
        contract = "0x" + new ContractRunner(factory).addContract(GAS_LEFT_CONTRACT).toHexString();
        eoa = "0x" + TestUtils.generateAddress("eoa").toHexString();
        ethModule = factory.getEthModule();
    }

    private static Stream<Arguments> callsBelowIntrinsicGas() {
        return Stream.of(
                // target, data, intrinsic gas
                Arguments.of("contract", null, "21000"),
                Arguments.of("eoa", null, "21000"),
                Arguments.of(REMASC, null, "21000"),
                Arguments.of(null, null, "53000"),      // empty create: 21,000 + 32,000
                Arguments.of(null, "0x01", "53018")     // 1-byte create: 53,000 + 16 + 2 (one RSKIP438 init-code word)
        );
    }

    @ParameterizedTest
    @MethodSource("callsBelowIntrinsicGas")
    void call_gasBelowIntrinsicCost_answersTransactionError(String target, String data, String intrinsic) {
        CallArguments args = callArgs(resolve(target), data);
        args.setGas("0x4e20"); // 20,000

        RskJsonRpcRequestException error = assertThrows(RskJsonRpcRequestException.class,
                () -> ethModule.call(toParam(args), latest()));

        assertEquals(TRANSACTION_ERROR, error.getCode());
        assertEquals("Not enough gas for transaction execution: tx needs: " + intrinsic + " tx sent: 20000",
                error.getMessage());
    }

    @Test
    void call_zeroPaddedGas_isReadByValue() {
        CallArguments args = callArgs(contract, null);
        args.setGas("0x000000000000000001");

        RskJsonRpcRequestException error = assertThrows(RskJsonRpcRequestException.class,
                () -> ethModule.call(toParam(args), latest()));

        assertEquals(TRANSACTION_ERROR, error.getCode());
        assertEquals("Not enough gas for transaction execution: tx needs: 21000 tx sent: 1", error.getMessage());
    }

    /**
     * 450,000 non-zero bytes cost 21,000 + 450,000 * 16 = 7,221,000 intrinsic gas, above the 6,800,000 cap.
     */
    @ParameterizedTest
    @MethodSource("targets")
    void estimateGas_intrinsicGasAboveTheCap_answersTransactionError(String target) {
        CallArguments args = callArgs(resolve(target), "0x" + "01".repeat(450_000));

        RskJsonRpcRequestException error = assertThrows(RskJsonRpcRequestException.class,
                () -> ethModule.estimateGas(toParam(args), latest()));

        assertEquals(TRANSACTION_ERROR, error.getCode());
        assertEquals("Not enough gas for transaction execution: tx needs: 7221000 tx sent: 6800000",
                error.getMessage());
    }

    /**
     * 400,000 non-zero bytes cost 21,000 + 400,000 * 16 = 6,421,000 = 0x61fa08 gas, just under the cap.
     */
    @Test
    void estimateGas_intrinsicGasJustUnderTheCap_returnsTheEstimate() {
        CallArguments args = callArgs(eoa, "0x" + "01".repeat(400_000));

        String estimate = ethModule.estimateGas(toParam(args), latest());

        assertEquals("0x61fa08", estimate);
    }

    private static Stream<String> targets() {
        return Stream.of("contract", "eoa", REMASC, IDENTITY, BRIDGE);
    }

    private String resolve(String target) {
        if ("contract".equals(target)) {
            return contract;
        }
        if ("eoa".equals(target)) {
            return eoa;
        }
        return target;
    }

    private static CallArguments callArgs(String to, String data) {
        CallArguments args = new CallArguments();
        args.setFrom("0x" + TestUtils.generateAddress("from").toHexString());
        args.setTo(to);
        args.setData(data);
        args.setGasPrice("0x0");
        args.setValue("0x0");
        return args;
    }

    private static CallArgumentsParam toParam(CallArguments args) {
        return TransactionFactoryHelper.toCallArgumentsParam(args);
    }

    private static BlockIdentifierParam latest() {
        return new BlockIdentifierParam("latest");
    }
}
