/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.bto;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class HDWalletUtilsTest {
    private HDWalletUtils contract;

    @Before
    public void createContract() {
        RskSystemProperties config = new TestSystemProperties();
        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        contract = spy(new HDWalletUtils(config.getActivationConfig(), new RskAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
        when(contract.getExecutionEnvironment()).thenReturn(executionEnvironment);
    }

    @Test
    public void hasNoDefaultMethod() {
        Assert.assertFalse(contract.getDefaultMethod().isPresent());
    }

    @Test
    public void hasFourMethods() {
        Assert.assertEquals(4, contract.getMethods().size());
    }

    @Test
    public void hasToBase58Check() {
        assertHasMethod(ToBase58Check.class);
    }

    @Test
    public void hasDeriveExtendedPublicKey() {
        assertHasMethod(DeriveExtendedPublicKey.class);
    }

    @Test
    public void hasExtractPublicKeyFromExtendedPublicKey() {
        assertHasMethod(ExtractPublicKeyFromExtendedPublicKey.class);
    }

    @Test
    public void hasGetMultisigScriptHash() {
        assertHasMethod(GetMultisigScriptHash.class);
    }

    private void assertHasMethod(Class clazz) {
        Optional<NativeMethod> method = contract.getMethods().stream()
                .filter(m -> m.getClass() == clazz).findFirst();
        Assert.assertTrue(method.isPresent());
    }
}