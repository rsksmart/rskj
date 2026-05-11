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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.rsk.core.RskAddress;

/**
 * Tests only EIP1884 related part of RSKIP555 changes.
 */
class Rskip555Eip1884Tests extends AbstractEvmTester {

    @BeforeEach
    void setupTest() {
        initActivationConfig(true);
        invoke = new ProgramInvokeMockImpl();
    }

    void initActivationConfig(boolean withRskip555) {
        if (withRskip555) {
            activationConfig = ActivationConfigsForTest.all();
        } else {
            activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP555);
        }
    }

    @Test
    void balanceGasCost() {
        RskAddress accountAddress = new RskAddress(invoke.getOwnerAddress());
        DataWord balance = DataWord.valueOf(invoke.getRepository().getBalance(accountAddress).getBytes());

        byte[] smartContractBytes = compiler.compile(
                "PUSH20 0x" + accountAddress.toHexString() + " BALANCE");
        Program program = executeSmartContract(createTransaction(), smartContractBytes);

        assertEquals(1, program.getStack().size());
        assertEquals(balance, program.stackPop());
        assertEquals(
                GasCost.FASTESTSTEP + GasCost.getBalance(activationConfig.forBlock(0)),
                program.getResult().getGasUsed());

        initActivationConfig(false);

        program = executeSmartContract(createTransaction(), smartContractBytes);
        assertEquals(
                GasCost.FASTESTSTEP + GasCost.getBalance(activationConfig.forBlock(0)),
                program.getResult().getGasUsed());
    }

    @Test
    void extCodeHashGasCost() {
        RskAddress contractAddress = ProgramInvokeMockImpl.CONTRACT_ADDRESS_DEFAULT;
        byte[] contractCode = invoke.getRepository().getCode(contractAddress);

        byte[] smartContractBytes = compiler.compile(
                "PUSH20 0x" + contractAddress.toHexString() + " EXTCODEHASH");
        Program program = executeSmartContract(createTransaction(), smartContractBytes);

        assertEquals(1, program.getStack().size());
        assertEquals(DataWord.valueOf(Keccak256Helper.keccak256(contractCode)), program.stackPop());
        assertEquals(
                GasCost.FASTESTSTEP + GasCost.getExtCodeHash(activationConfig.forBlock(0)),
                program.getResult().getGasUsed());

        initActivationConfig(false);

        program = executeSmartContract(createTransaction(), smartContractBytes);
        assertEquals(
                GasCost.FASTESTSTEP + GasCost.getExtCodeHash(activationConfig.forBlock(0)),
                program.getResult().getGasUsed());
    }

    @Test
    void selfBalanceGasCost() {
        RskAddress accountAddress = new RskAddress(invoke.getOwnerAddress());
        DataWord balance = DataWord.valueOf(invoke.getRepository().getBalance(accountAddress).getBytes());

        byte[] smartContractBytes = compiler.compile("SELFBALANCE");
        Program program = executeSmartContract(createTransaction(), smartContractBytes);

        assertEquals(1, program.getStack().size());
        assertEquals(balance, program.stackPop());
        assertEquals(GasCost.FASTSTEP, program.getResult().getGasUsed());

        initActivationConfig(false);

        program = executeSmartContract(createTransaction(), smartContractBytes);
        assertEquals(1, program.getStack().size());
        assertEquals(balance, program.stackPop());
        assertEquals(GasCost.FASTSTEP, program.getResult().getGasUsed());
    }
}
