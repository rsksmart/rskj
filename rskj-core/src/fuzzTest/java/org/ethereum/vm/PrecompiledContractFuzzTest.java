package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Assumptions;
import org.opentest4j.TestAbortedException;
import org.spongycastle.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;

public class PrecompiledContractFuzzTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

    @Tag("PrecompiledContractFuzzIdentityRandom")
    @FuzzTest
    void identityFuzzTest(FuzzedDataProvider data) throws VMException {
        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000004");
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] d = data.consumeBytes(1200000);
        byte[] expected = Arrays.clone(d);
        contract.getGasForData(d);
        byte[] result = contract.execute(d);
        assertArrayEquals(expected, result);
    }

    @Tag("PrecompiledContractFuzzSha256Random")
    @FuzzTest
    void sha256FuzzTest(FuzzedDataProvider data) throws VMException {
        DataWord addr = PrecompiledContracts.SHA256_ADDR_DW;
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] d = data.consumeBytes(1200000);
        contract.getGasForData(d);
        byte[] result = contract.execute(d);
        assertEquals(32, result.length);
    }

    @Tag("PrecompiledContractFuzzRimpempdRandom")
    @FuzzTest
    void ripempdFuzzTest(FuzzedDataProvider data) throws VMException {
        DataWord addr = PrecompiledContracts.RIPEMPD160_ADDR_DW;
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] d = data.consumeBytes(1200000);
        contract.getGasForData(d);
        byte[] result = contract.execute(d);
        assertEquals(32, result.length);
    }

    @Tag("PrecompiledContractFuzzEcrecoverRandom")
    @FuzzTest
    void ecrecoverRandomFuzzTest(FuzzedDataProvider data) throws VMException {
        DataWord addr = PrecompiledContracts.ECRECOVER_ADDR_DW;
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] d = data.consumeBytes(1200000);
        contract.getGasForData(d);
        contract.execute(d);
    }
    
    @Tag("PrecompiledContractFuzzModexpRandom")
    @FuzzTest
    void modxpRandomFuzzTest(FuzzedDataProvider data) throws VMException {
        DataWord addr = PrecompiledContracts.BIG_INT_MODEXP_ADDR_DW;
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] d = data.consumeBytes(1200000);
        long gas = contract.getGasForData(d);
        try {
            Assumptions.assumeTrue(gas <= 6800000);
        } catch (TestAbortedException ignored) {
            return;
        }
        contract.execute(d);
    }

}
