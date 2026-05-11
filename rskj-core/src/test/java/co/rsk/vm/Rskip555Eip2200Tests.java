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
package co.rsk.vm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.LongStream;

import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.rsk.core.RskAddress;

class Rskip555Eip2200Tests extends AbstractEvmTester {

    private static final long TARGET_SLOT = 1;

    @BeforeEach
    void setupTest() {
        initActivationConfig(true);
        invoke = new ProgramInvokeMockImpl();
        invoke.setOwnerAddress(ProgramInvokeMockImpl.CONTRACT_ADDRESS_DEFAULT);
    }

    void initActivationConfig(boolean withRskip555) {
        if (withRskip555) {
            activationConfig = ActivationConfigsForTest.all();
        } else {
            activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP555);
        }
    }

    @Test
    void slotSstoreWithinOneContract() {
        long empty = 0;
        long a = 1;
        long b = 2;
        long c = 3;

        long sloadGas = GasCost.getSload(activationConfig.forBlock(0));
        long setGas = GasCost.SSTORE_SET_GAS + sloadGas;
        long resetGas = GasCost.SSTORE_RESET_GAS + sloadGas;
        long clearRefund = GasCost.SSTORE_CLEARS_SCHEDULE;
        long setRefund = GasCost.SSTORE_SET_GAS - sloadGas;
        long resetRefund = GasCost.SSTORE_RESET_GAS - sloadGas;

        // [empty]-empty
        validateSlotTransitions(sloadGas, 0, empty, empty);
        // [a]-a
        validateSlotTransitions(sloadGas, 0, a, a);
        // [empty]-a
        validateSlotTransitions(GasCost.SSTORE_SET_GAS, 0, empty, a);
        // [a]-empty
        validateSlotTransitions(GasCost.SSTORE_RESET_GAS, clearRefund, a, empty);
        // [a]-b
        validateSlotTransitions(GasCost.SSTORE_RESET_GAS, 0, a, b);
        // [empty]-a-empty
        validateSlotTransitions(setGas, setRefund, empty, a, empty);
        // [empty]-a-b
        validateSlotTransitions(setGas, 0, empty, a, b);
        // [empty]-a-a
        validateSlotTransitions(setGas, 0, empty, a, a);
        // [a]-empty-a
        validateSlotTransitions(resetGas, resetRefund, a, empty, a);
        // [a]-empty-b
        validateSlotTransitions(resetGas, 0, a, empty, b);
        // [a]-empty-empty
        validateSlotTransitions(resetGas, clearRefund, a, empty, empty);
        // [a]-b-empty
        validateSlotTransitions(resetGas, clearRefund, a, b, empty);
        // [a]-b-a
        validateSlotTransitions(resetGas, resetRefund, a, b, a);
        // [a]-b-c
        validateSlotTransitions(resetGas, 0, a, b, c);
        // [a]-b-b
        validateSlotTransitions(resetGas, 0, a, b, b);
    }

    /**
     * Contains same test cases as {@link slotSstoreWithinOneContract} but for cross
     * smart contract calls.
     *
     * Gas price and refund values must be the same minus non test related
     * instructions overhead
     */
    @Test
    void slotSstoreCrossSmartContract() {
        long empty = 0;
        long a = 1;
        long b = 2;
        long c = 3;

        long sloadGas = GasCost.getSload(activationConfig.forBlock(0));
        long setGas = GasCost.SSTORE_SET_GAS + sloadGas;
        long resetGas = GasCost.SSTORE_RESET_GAS + sloadGas;
        long clearRefund = GasCost.SSTORE_CLEARS_SCHEDULE;
        long setRefund = GasCost.SSTORE_SET_GAS - sloadGas;
        long resetRefund = GasCost.SSTORE_RESET_GAS - sloadGas;

        // [empty]-empty
        validateNestedSlotTransitions(sloadGas, 0, empty, empty);
        // [a]-a
        validateNestedSlotTransitions(sloadGas, 0, a, a);
        // [empty]-a
        validateNestedSlotTransitions(GasCost.SSTORE_SET_GAS, 0, empty, a);
        // [a]-empty
        validateNestedSlotTransitions(GasCost.SSTORE_RESET_GAS, clearRefund, a, empty);
        // [a]-b
        validateNestedSlotTransitions(GasCost.SSTORE_RESET_GAS, 0, a, b);
        // [empty]-a-empty
        validateNestedSlotTransitions(setGas, setRefund, empty, a, empty);
        // [empty]-a-b
        validateNestedSlotTransitions(setGas, 0, empty, a, b);
        // [empty]-a-a
        validateNestedSlotTransitions(setGas, 0, empty, a, a);
        // [a]-empty-a
        validateNestedSlotTransitions(resetGas, resetRefund, a, empty, a);
        // [a]-empty-b
        validateNestedSlotTransitions(resetGas, 0, a, empty, b);
        // [a]-empty-empty
        validateNestedSlotTransitions(resetGas, clearRefund, a, empty, empty);
        // [a]-b-empty
        validateNestedSlotTransitions(resetGas, clearRefund, a, b, empty);
        // [a]-b-a
        validateNestedSlotTransitions(resetGas, resetRefund, a, b, a);
        // [a]-b-c
        validateNestedSlotTransitions(resetGas, 0, a, b, c);
        // [a]-b-b
        validateNestedSlotTransitions(resetGas, 0, a, b, b);
    }

    @Test
    void slotSstoreWithinOneContractBeforeRskip555() {
        initActivationConfig(false);

        long empty = 0;
        long a = 1;
        long b = 2;
        long c = 3;

        long setGas = GasCost.SSTORE_SET_GAS;
        long resetGas = GasCost.SSTORE_RESET_GAS;
        long clearGas = GasCost.CLEAR_SSTORE;
        long setClearGas = setGas + clearGas;
        long setResetGas = setGas + resetGas;
        long clearResetGas = clearGas + resetGas;
        long clearRefund = GasCost.SSTORE_CLEARS_SCHEDULE;

        // [empty]-empty
        validateSlotTransitions(resetGas, 0, empty, empty);
        // [a]-a
        validateSlotTransitions(resetGas, 0, a, a);
        // [empty]-a
        validateSlotTransitions(setGas, 0, empty, a);
        // [a]-empty
        validateSlotTransitions(clearGas, clearRefund, a, empty);
        // [a]-b
        validateSlotTransitions(resetGas, 0, a, b);
        // [empty]-a-empty
        validateSlotTransitions(setClearGas, clearRefund, empty, a, empty);
        // [empty]-a-b
        validateSlotTransitions(setResetGas, 0, empty, a, b);
        // [empty]-a-a
        validateSlotTransitions(setResetGas, 0, empty, a, a);
        // [a]-empty-a
        validateSlotTransitions(setClearGas, clearRefund, a, empty, a);
        // [a]-empty-b
        validateSlotTransitions(setClearGas, clearRefund, a, empty, b);
        // [a]-empty-empty
        validateSlotTransitions(clearResetGas, clearRefund, a, empty, empty);
        // [a]-b-empty
        validateSlotTransitions(clearResetGas, clearRefund, a, b, empty);
        // [a]-b-a
        validateSlotTransitions(resetGas * 2, 0, a, b, a);
        // [a]-b-c
        validateSlotTransitions(resetGas * 2, 0, a, b, c);
        // [a]-b-b
        validateSlotTransitions(resetGas * 2, 0, a, b, b);
    }

    /**
     * Contains same test cases as {@link slotSstoreWithinOneContractBeforeRskip555}
     * but for cross smart contract calls.
     *
     * Gas price and refund values must be the same minus non test related
     * instructions overhead
     */
    @Test
    void slotSstoreCrossSmartContractBeforeRskip555() {
        initActivationConfig(false);

        long empty = 0;
        long a = 1;
        long b = 2;
        long c = 3;

        long setGas = GasCost.SSTORE_SET_GAS;
        long resetGas = GasCost.SSTORE_RESET_GAS;
        long clearGas = GasCost.CLEAR_SSTORE;
        long setClearGas = setGas + clearGas;
        long setResetGas = setGas + resetGas;
        long clearResetGas = clearGas + resetGas;
        long clearRefund = GasCost.SSTORE_CLEARS_SCHEDULE;

        // [empty]-empty
        validateNestedSlotTransitions(resetGas, 0, empty, empty);
        // [a]-a
        validateNestedSlotTransitions(resetGas, 0, a, a);
        // [empty]-a
        validateNestedSlotTransitions(setGas, 0, empty, a);
        // [a]-empty
        validateNestedSlotTransitions(clearGas, clearRefund, a, empty);
        // [a]-b
        validateNestedSlotTransitions(resetGas, 0, a, b);
        // [empty]-a-empty
        validateNestedSlotTransitions(setClearGas, clearRefund, empty, a, empty);
        // [empty]-a-b
        validateNestedSlotTransitions(setResetGas, 0, empty, a, b);
        // [empty]-a-a
        validateNestedSlotTransitions(setResetGas, 0, empty, a, a);
        // [a]-empty-a
        validateNestedSlotTransitions(setClearGas, clearRefund, a, empty, a);
        // [a]-empty-b
        validateNestedSlotTransitions(setClearGas, clearRefund, a, empty, b);
        // [a]-empty-empty
        validateNestedSlotTransitions(clearResetGas, clearRefund, a, empty, empty);
        // [a]-b-empty
        validateNestedSlotTransitions(clearResetGas, clearRefund, a, b, empty);
        // [a]-b-a
        validateNestedSlotTransitions(resetGas * 2, 0, a, b, a);
        // [a]-b-c
        validateNestedSlotTransitions(resetGas * 2, 0, a, b, c);
        // [a]-b-b
        validateNestedSlotTransitions(resetGas * 2, 0, a, b, b);
    }

    @Test
    void sstoreFailsWhenGasLeftIsLessThanOrEqualToStipend() {
        initSstoreContract(332211);
        invoke.setGas(GasCost.STIPEND_CALL + GasCost.FASTESTSTEP * 2);

        Program.OutOfGasException exception = assertThrows(Program.OutOfGasException.class, this::executeSmartContract);
        assertTrue(exception.getMessage().contains("Gas value overflow"));
        assertEquals(DataWord.ZERO, readSmartContractSlot());

        initActivationConfig(false);
        exception = assertThrows(Program.OutOfGasException.class, this::executeSmartContract);
        assertTrue(exception.getMessage().contains("Not enough gas"));
        assertEquals(DataWord.ZERO, readSmartContractSlot());
    }

    /**
     * Build/execute SC and validate gas+refund for slot transitions.
     *
     * All logic is executed inside single SC bytecode.
     */
    void validateSlotTransitions(long expectedGasCost, long expectedRefund, long initialSlotValue,
            long... slotValueUpdates) {
        if (slotValueUpdates.length == 0) {
            throw new IllegalArgumentException("At least one slot value update is required");
        }

        String transition = buildTransitionString(LongStream.concat(
                LongStream.of(initialSlotValue),
                LongStream.of(slotValueUpdates)).toArray());

        setTargetSlotValue(invoke.getContractAddress(), DataWord.valueOf(initialSlotValue));

        var code = this.buildSstoreCode(slotValueUpdates);
        var bytes = compiler.compile(code);
        invoke.initDefaultContract(bytes, null);

        Program program = executeSmartContract();

        assertEquals(
                DataWord.valueOf(slotValueUpdates[slotValueUpdates.length - 1]),
                readSmartContractSlot(),
                transition);

        assertEquals(
                expectedGasCost,
                deductSstoreGas(program, slotValueUpdates.length),
                transition);
        assertEquals(
                expectedRefund,
                program.getResult().getFutureRefund(),
                transition);
    }

    /**
     * Build/execute cross smart contract flow where single slot update happens in
     * child smart contract.
     */
    void validateNestedSlotTransitions(long expectedGasCost, long expectedRefund, long initialSlotValue,
            long... slotValueUpdates) {
        if (slotValueUpdates.length == 0) {
            throw new IllegalArgumentException("At least one slot value update is required");
        }

        String transition = buildTransitionString(LongStream.concat(
                LongStream.of(initialSlotValue),
                LongStream.of(slotValueUpdates)).toArray());

        RskAddress nestedAddress = ProgramInvokeMockImpl.CONTRACT_ADDRESS_NESTED_DEFAULT;
        setTargetSlotValue(nestedAddress, DataWord.valueOf(initialSlotValue));

        initCallableContract();
        initCallerContract(slotValueUpdates);

        Program program = executeSmartContract();

        assertEquals(
                DataWord.valueOf(slotValueUpdates[slotValueUpdates.length - 1]),
                readTargetSlotValue(nestedAddress),
                transition);

        assertEquals(
                expectedGasCost,
                deductCallerCodeGas(program.getResult().getGasUsed(), slotValueUpdates.length),
                transition);
        assertEquals(
                expectedRefund,
                program.getResult().getFutureRefund(),
                transition);
    }

    String buildTransitionString(long... slotValues) {
        StringBuilder transition = new StringBuilder("Transition:");
        for (int i = 0; i < slotValues.length; i++) {
            if (i > 0) {
                transition.append("->");
            }
            transition.append(slotValues[i]);
        }
        return transition.toString();
    }

    void initSstoreContract(long... values) {
        var code = this.buildSstoreCode(values);
        var bytes = compiler.compile(code);
        invoke.initDefaultContract(bytes, null);
    }

    void initCallerContract(long... values) {
        var code = this.buildCallerCode(ProgramInvokeMockImpl.CONTRACT_ADDRESS_NESTED_DEFAULT, values);
        var bytes = compiler.compile(code);
        invoke.initDefaultContract(bytes, null);
    }

    void initCallableContract() {
        var code = this.buildCallableCode();
        var bytes = compiler.compile(code);
        invoke.initNestedContract(bytes, null);
    }

    void setTargetSlotValue(RskAddress address, DataWord value) {
        setSlotValue(address, DataWord.valueOf(TARGET_SLOT), value);
    }

    DataWord readTargetSlotValue(RskAddress address) {
        return readSlotValue(address, DataWord.valueOf(TARGET_SLOT));
    }

    void setSmartContractSlot(long value) {
        setTargetSlotValue(invoke.getContractAddress(), DataWord.valueOf(value));
    }

    DataWord readSmartContractSlot() {
        return readTargetSlotValue(invoke.getContractAddress());
    }

    String buildCallerCode(RskAddress callableAddress, long... values) {
        StringBuilder code = new StringBuilder();
        for (long value : values) {
            // memory[0..32] = value
            code.append("PUSH32 0x").append(DataWord.valueOf(value)).append(' ');
            code.append("PUSH1 0x00 ");
            code.append("MSTORE ");

            // CALL(gas, addr, value, argsOffset, argsLength, retOffset, retLength)
            // Pushed in reverse so the top of stack is `gas`.
            code.append("PUSH1 0x00 "); // retLength
            code.append("PUSH1 0x00 "); // retOffset
            code.append("PUSH1 0x20 "); // argsLength = 32
            code.append("PUSH1 0x00 "); // argsOffset
            code.append("PUSH1 0x00 "); // value (no ETH)
            code.append("PUSH20 0x").append(callableAddress).append(' ');
            code.append("GAS "); // forward all gas
            code.append("CALL ");
            code.append("POP "); // discard success flag
        }
        code.append("STOP");
        return code.toString();
    }

    /**
     * Returns gas cost minus the caller-side overhead from {@link #buildCallerCode}
     * and the nested callee overhead from {@link #buildCallableCode}.
     */
    private long deductCallerCodeGas(long gasUsed, int callOps) {
        var perCallCallerOverhead = GasCost.FASTESTSTEP * 9 + GasCost.QUICKSTEP * 2 + GasCost.CALL;

        for (int i = 0; i < callOps; i++) {
            gasUsed = deductCallableCodeGas(gasUsed);
        }

        return gasUsed - GasCost.MEMORY - perCallCallerOverhead * callOps;
    }

    String buildCallableCode() {
        return "PUSH1 0x00 " + // calldata offset
                "CALLDATALOAD " + // value -> stack
                "PUSH32 0x" + DataWord.valueOf(TARGET_SLOT) + " " +
                "SSTORE " +
                "STOP";
    }

    /**
     * Returns gas cost minus the fixed overhead introduced by
     * {@link #buildCallableCode}:
     * PUSH1 + CALLDATALOAD + PUSH32 (the callable always executes as a single
     * block).
     */
    private long deductCallableCodeGas(long gasUsed) {
        var overhead = GasCost.FASTESTSTEP * 3;
        return gasUsed - overhead;
    }

    String buildSstoreCode(long... values) {
        StringBuilder code = new StringBuilder();

        for (long value : values) {
            code.append("PUSH32 0x").append(DataWord.valueOf(value)).append(' ');
            code.append("PUSH32 0x").append(DataWord.valueOf(TARGET_SLOT)).append(' ');
            code.append("SSTORE ");
        }

        code.append("STOP");
        return code.toString();
    }

    /**
     * Returns gas cost minus costs overhead for PUSH32 operations from
     * {@link buildSstoreCode} output.
     */
    private long deductSstoreGas(Program program, int sstoreOperations) {
        var perOpOverhead = GasCost.FASTESTSTEP * 2;
        return program.getResult().getGasUsed() - perOpOverhead * sstoreOperations;
    }

}
