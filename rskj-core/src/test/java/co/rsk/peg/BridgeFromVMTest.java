/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.asm.EVMAssembler;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class BridgeFromVMTest {
    private static RskSystemProperties config = new RskSystemProperties();
    private BlockchainNetConfig oldConfig;
    private BlockchainNetConfig testConfig;

    @Test
    public void testCallFromContract_preRfs90() {
        try {
            runBridgeCallFromContract(false);
            Assert.fail();
        } catch (RuntimeException e) {}
    }

    @Test
    public void testCallFromContract() {
        Program result = runBridgeCallFromContract(true);
        long output = new BigInteger(result.memoryChunk(0x30, 0x20)).longValue();
        Assert.assertEquals(BridgeRegTestConstants.getInstance().getMinimumLockTxValue().value, output);
    }

    private Program runBridgeCallFromContract(boolean isRfs90) {
        BlockchainConfig blockchainConfig = simulateConfig(isRfs90);
        config.setBlockchainConfig(testConfig);

        EVMAssembler assembler = new EVMAssembler();
        ProgramInvoke invoke = new ProgramInvokeMockImpl();

        // Save code on the sender's address so that the bridge
        // thinks its being called by a contract
        byte[] callerCode = assembler.assemble("0xaabb 0xccdd 0xeeff");
        invoke.getRepository().saveCode(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()), callerCode);

        PrecompiledContracts precompiledContracts = mock(PrecompiledContracts.class);
        doReturn(new Bridge(config, PrecompiledContracts.BRIDGE_ADDR))
                .when(precompiledContracts)
                .getContractForAddress(new DataWord(PrecompiledContracts.BRIDGE_ADDR_STR));

        VM vm = new VM(config.getVmConfig(), precompiledContracts);

        // Encode a call to the bridge's getMinimumLockTxValue function
        // That means first pushing the corresponding encoded ABI storage to memory (MSTORE)
        // and then doing a CALL to the corresponding address with the correct parameters
        String bridgeFunctionHex = Hex.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32*2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x00 0x0000000000000000000000000000000001000006 0x1000 CALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("00000000000000000000000000000000000000000000000000000000aabbccdd"));

        // Run the program on the VM
        Program program = new Program(config.getVmConfig(), precompiledContracts, code, invoke, tx);
        for (int i = 0; i < numOps; i++) {
            vm.step(program);
        }
        restoreConfig();

        // Return the resulting program (and its state)
        return program;
    }

    private BlockchainConfig simulateConfig(boolean isRfs90) {
        oldConfig = config.getBlockchainConfig();
        testConfig = spy(RegTestConfig.class);
        BlockchainConfig preRfs90Config = spy(RegTestConfig.class);
        when(preRfs90Config.isRfs90()).thenReturn(isRfs90);
        when(testConfig.getConfigForBlock(any(Long.class))).thenReturn(preRfs90Config);
        Assert.assertEquals(isRfs90, testConfig.getConfigForBlock(1L).isRfs90());
        return preRfs90Config;
    }

    private void restoreConfig() {
        config.setBlockchainConfig(oldConfig);
    }
}
