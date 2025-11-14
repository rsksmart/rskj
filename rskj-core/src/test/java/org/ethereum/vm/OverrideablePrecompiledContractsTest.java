package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSupportFactory;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverrideablePrecompiledContractsTest {

    private final TestSystemProperties testSystemProperties = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = getPrecompiledContracts();
    private final OverrideablePrecompiledContracts overrideablePrecompiledContracts = new OverrideablePrecompiledContracts(precompiledContracts);

    @Test
    void getContractForAddress_whenNotOverridden_returnsOriginalContract() {
        // Given
        RskAddress contractAddress = new RskAddress("0000000000000000000000000000000000000001");
        ActivationConfig.ForBlock blockActivations = testSystemProperties.getActivationConfig().forBlock(1L);

        // When
        DataWord contractAddressInDataword = DataWord.valueFromHex(contractAddress.toHexString());
        PrecompiledContracts.PrecompiledContract originalContract = precompiledContracts.getContractForAddress(blockActivations, contractAddressInDataword);
        PrecompiledContracts.PrecompiledContract result = overrideablePrecompiledContracts.getContractForAddress(blockActivations, contractAddressInDataword);

        // Then
        assertEquals(originalContract, result);
    }

    @Test
    void getContractForAddress_whenOverridden_returnsOverriddenContract() {
        // Given
        RskAddress contractAddress = new RskAddress("0x0000000000000000000000000000000000000001");
        RskAddress movePrecompiledTo = new RskAddress("0x000000000000000000000000000000000000002");
        ActivationConfig.ForBlock blockActivations = testSystemProperties.getActivationConfig().forBlock(1L);

        // When
        DataWord contractAddressInDataword = DataWord.valueFromHex(contractAddress.toHexString());
        DataWord movePrecompileToInDataword = DataWord.valueFromHex(movePrecompiledTo.toHexString());
        overrideablePrecompiledContracts.addOverride(contractAddress, movePrecompiledTo, blockActivations);
        PrecompiledContracts.PrecompiledContract originalContract = precompiledContracts.getContractForAddress(blockActivations, contractAddressInDataword);
        PrecompiledContracts.PrecompiledContract result = overrideablePrecompiledContracts.getContractForAddress(blockActivations, movePrecompileToInDataword);

        // Then
        assertEquals(originalContract, result);
    }

    @Test
    void isOverridden_executesAsExpected() {
        // Given
        RskAddress contractAddress = new RskAddress("0x0000000000000000000000000000000000000001");
        RskAddress movePrecompiledTo = new RskAddress("0x000000000000000000000000000000000000002");
        RskAddress witnessAddress = new RskAddress("0x0000000000000000000000000000000000000003");
        ActivationConfig.ForBlock blockActivations = testSystemProperties.getActivationConfig().forBlock(1L);

        // When
        overrideablePrecompiledContracts.addOverride(contractAddress, movePrecompiledTo, blockActivations);

        // Then
        assertTrue(overrideablePrecompiledContracts.isOverridden(contractAddress));
        assertFalse(overrideablePrecompiledContracts.isOverridden(witnessAddress));
    }

    @Test
    void addOverride_noPrecompiledContractToMove_throwsExceptionAsExpected() {
        // Given
        RskAddress contractAddress = new RskAddress("0x0000000000000000000000000000000000000000"); // No Precompiled here
        RskAddress movePrecompiledTo = new RskAddress("0x000000000000000000000000000000000000002");
        ActivationConfig.ForBlock blockActivations = testSystemProperties.getActivationConfig().forBlock(1L);

        // When
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            overrideablePrecompiledContracts.addOverride(contractAddress, movePrecompiledTo, blockActivations);
        });

        // Then
        assertEquals("Account " + contractAddress.toHexString() + " is not a precompiled contract", exception.getMessage());
    }

    @Test
    void addOverride_precompiledAlreadyOverridden_throwsExceptionAsExpected() {
        // Given
        RskAddress contractAddress1 = new RskAddress("0x0000000000000000000000000000000000000001"); // Precompile 1
        RskAddress contractAddress2 = new RskAddress("0x000000000000000000000000000000000000002"); // Precompile 2
        RskAddress movePrecompileTo = new RskAddress("0x0000000000000000000000000000000000000003"); // Same Target
        ActivationConfig.ForBlock blockActivations = testSystemProperties.getActivationConfig().forBlock(1L);

        // When
        overrideablePrecompiledContracts.addOverride(contractAddress1, movePrecompileTo, blockActivations);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            // This will try to move a precompiled contract to the same target address
            overrideablePrecompiledContracts.addOverride(contractAddress2, movePrecompileTo, blockActivations);
        });

        // Then
        assertEquals("Account " + movePrecompileTo.toHexString() + " is already overridden", exception.getMessage());
    }

    private PrecompiledContracts getPrecompiledContracts() {
        TestSystemProperties config = new TestSystemProperties();
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                null, null, null, signatureCache);
        return new PrecompiledContracts(config, bridgeSupportFactory, signatureCache);
    }

}
