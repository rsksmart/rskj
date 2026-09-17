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
package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.ethereum.core.DelegationCodeResolver.createDelegatedCode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelegationCodeResolverTest {

    ActivationConfig.ForBlock activationConfig;
    Repository repository;

    @BeforeEach
    void setUp() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        repository = mock(Repository.class);
    }

    @Test
    void createDelegatedCode_nullAddress_throws() {
        assertThrows(IllegalStateException.class,
                () -> createDelegatedCode(RskAddress.nullAddress()));
    }

    @Test
    void createDelegatedCode_zeroAddress_throws() {
        assertThrows(IllegalStateException.class,
                () -> createDelegatedCode(RskAddress.ZERO_ADDRESS));
    }

    @Test
    void createDelegatedCode_roundTripsThroughExtract() {
        RskAddress delegate = new RskAddress("0x00000000000000000000000000000000000000ab");
        byte[] code = createDelegatedCode(delegate);

        assertTrue(DelegationCodeResolver.isDelegatedCode(code));
        assertArrayEquals(delegate.getBytes(), DelegationCodeResolver.extractDelegatedAddress(code).getBytes());
    }

    @Test
    void isDelegatedCode_wrongPrefix_returnsFalse() {
        byte[] code = createDelegatedCode(
                new RskAddress("0x0000000000000000000000000000000000000001"));
        code[0] = 0x00;

        assertFalse(DelegationCodeResolver.isDelegatedCode(code));
    }

    @Test
    void getExecutionCode_returnsEmptyWhenTargetAccountDoesNotExist() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");

        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);
        when(repository.isExist(target)).thenReturn(false);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, code);
    }

    @Test
    void getExecutionCode_returnsEmptyWhenTargetHasNoCode() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(ByteUtil.EMPTY_BYTE_ARRAY);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, code);
    }

    @Test
    void getExecutionCode_returnsEmptyWhenDelegatedAddressIsPrecompile() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(createDelegatedCode(delegated));
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                delegated::equals,
                activationConfig
        );

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, code);
    }

    @Test
    void getExecutionCode_returnsDelegatedCodeWhenDelegatedAccountExists() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        byte[] delegatedRuntimeCode = new byte[] { 0x01, 0x02, 0x03 };

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(createDelegatedCode(delegated));

        when(repository.isExist(delegated)).thenReturn(true);
        when(repository.getCode(delegated)).thenReturn(delegatedRuntimeCode);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(delegatedRuntimeCode, code);
    }

    @Test
    void getExecutionCode_returnsDelegatedCodeWithoutResolvingDelegationChain() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new  RskAddress("0000000000000000000000000000000000000002");
        RskAddress secondDelegation = new RskAddress("0000000000000000000000000000000000000003");

        byte[] delegatedCode = createDelegatedCode(secondDelegation);

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(createDelegatedCode(delegated));

        when(repository.isExist(delegated)).thenReturn(true);
        when(repository.getCode(delegated)).thenReturn(delegatedCode);

        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(delegatedCode, code);
    }

    @Test
    void getExecutionCode_returnsEmptyWhenTargetCodeIsNull() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(null);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, code);
    }

    @Test
    void getExecutionCode_returnsEmptyWhenDelegatedAccountCodeIsLength0() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(new byte[0]);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, code);
    }

    @Test
    void getExecutionCode_returnsDelegatedCodeWhenDelegatedAddressIsNotPrecompileAndExists() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        byte[] targetDelegatedCode = createDelegatedCode(delegated);
        byte[] delegatedRuntimeCode = new byte[] { 0x01, 0x02, 0x03 };

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(targetDelegatedCode);

        when(repository.isExist(delegated)).thenReturn(true);
        when(repository.getCode(delegated)).thenReturn(delegatedRuntimeCode);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(delegatedRuntimeCode, code);
    }

    @Test
    void getExecutionCode_returnsEmptyWhenDelegatedAddressIsNotPrecompileAndDoesNotExist() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        byte[] targetDelegatedCode = createDelegatedCode(delegated);
        byte[] delegatedRuntimeCode = new byte[] { 0x01, 0x02, 0x03 };

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(targetDelegatedCode);

        when(repository.isExist(delegated)).thenReturn(false);
        when(repository.getCode(delegated)).thenReturn(delegatedRuntimeCode);

        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, code);
    }

    @Test
    void getExecutionCode_returnsTargetCodeWhenCodeIsNotDelegated() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        byte[] runtimeCode = new byte[] { 0x01, 0x02, 0x03 };

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(runtimeCode);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(runtimeCode, code);
    }

    @Test
    void getExecutionCode_beforeRskip545Activation_returnsLiteralDelegationCode() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        byte[] delegationCode = createDelegatedCode(delegated);
        byte[] delegatedRuntimeCode = new byte[] { 0x01, 0x02, 0x03 };

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(delegationCode);
        when(repository.isExist(delegated)).thenReturn(true);
        when(repository.getCode(delegated)).thenReturn(delegatedRuntimeCode);

        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(false);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(delegationCode, code);
    }

    @Test
    void getExecutionCode_atRskip545Activation_resolvesDelegatedCode() {
        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        byte[] delegationCode = createDelegatedCode(delegated);
        byte[] delegatedRuntimeCode = new byte[] { 0x01, 0x02, 0x03 };

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(delegationCode);

        when(repository.isExist(delegated)).thenReturn(true);
        when(repository.getCode(delegated)).thenReturn(delegatedRuntimeCode);

        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(true);

        byte[] code = DelegationCodeResolver.getExecutionCode(repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(delegatedRuntimeCode, code);
    }

    @Test
    void getExecutionCode_beforeRskip545Activation_doesNotResolveDelegatedAddress() {
        ActivationConfig.ForBlock activationConfig = mock(ActivationConfig.ForBlock.class);

        RskAddress target = new RskAddress("0000000000000000000000000000000000000001");
        RskAddress delegated = new RskAddress("0000000000000000000000000000000000000002");

        byte[] delegationCode = createDelegatedCode(delegated);

        when(repository.isExist(target)).thenReturn(true);
        when(repository.getCode(target)).thenReturn(delegationCode);
        when(activationConfig.isActive(ConsensusRule.RSKIP545)).thenReturn(false);

        byte[] code = DelegationCodeResolver.getExecutionCode(
                repository,
                target,
                address -> false,
                activationConfig
        );

        assertArrayEquals(delegationCode, code);

        verify(repository, never()).isExist(delegated);
        verify(repository, never()).getCode(delegated);
    }

}
