/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package co.rsk.pcc.environment;

import co.rsk.config.TestSystemProperties;
import co.rsk.pcc.NativeContract;
import co.rsk.pcc.NativeMethod;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContractArgsBuilder;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnvironmentTest {
    private Environment contract;

    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("3000000");
    private static final String DATA = "80af2871";
    private static final String ENVIRONMENT_CONTRACT_ADDRESS = "0000000000000000000000000000000000000000000000000000000001000011";

    private CallTransaction.Function getCallStackDepthFunction;
    private PrecompiledContractArgs args;

    @BeforeEach
    void setup() {
        TestSystemProperties config = new TestSystemProperties();
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, signatureCache);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP203)).thenReturn(true);

        contract = (Environment) precompiledContracts.getContractForAddress(activations, DataWord.valueFromHex(ENVIRONMENT_CONTRACT_ADDRESS));

        getCallStackDepthFunction = getContractFunction(contract, GetCallStackDepth.class);

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.ENVIRONMENT_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        ProgramInvoke programInvoke = mock(ProgramInvoke.class);
        when(programInvoke.getCallDeep()).thenReturn(1);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        args = PrecompiledContractArgsBuilder.builder()
                .transaction(rskTx)
                .programInvoke(programInvoke)
                .executionBlock(executionBlock)
                .build();
    }

    @Test
    void hasNoDefaultMethod() {
        Assertions.assertFalse(contract.getDefaultMethod().isPresent());
    }

    @Test
    void hasOneMethod() {
        Assertions.assertEquals(1, contract.getMethods().size());
    }

    @Test
    void hasGetCallStackDepth() {
        assertHasMethod(GetCallStackDepth.class);
    }

    @Test
    void getCallStackDepth() throws VMException {
        contract.init(args);

        String h = HexUtils.toJsonHex(getCallStackDepthFunction.encode());
        byte[] encodedResult = contract.execute(getCallStackDepthFunction.encode());
        Object[] decodedResult = getCallStackDepthFunction.decodeResult(encodedResult);

        byte[] callStackDepth = (byte[]) decodedResult[0];
        byte[] expected = ByteUtil.intToBytes(1);

        assertArrayEquals(expected, callStackDepth);
    }

    private void assertHasMethod(Class clazz) {
        Optional<NativeMethod> method = contract.getMethods().stream()
                .filter(m -> m.getClass() == clazz).findFirst();
        Assertions.assertTrue(method.isPresent());
    }

    private CallTransaction.Function getContractFunction(NativeContract contract, Class methodClass) {
        Optional<NativeMethod> method = contract.getMethods().stream().filter(m -> m.getClass() == methodClass).findFirst();
        assertTrue(method.isPresent());
        return method.get().getFunction();
    }
}
