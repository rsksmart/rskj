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
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Optional;

import static org.mockito.Mockito.mock;

public class BTOUtilsTest {
    private RskSystemProperties config;
    private ExecutionEnvironment executionEnvironment;
    private BTOUtils contract;

    @Before
    public void createContract() {
        config = new TestSystemProperties();
        executionEnvironment = mock(ExecutionEnvironment.class);
        contract = new BTOUtils(config, new RskAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        Whitebox.setInternalState(contract, "executionEnvironment", executionEnvironment);
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
        assertHasMethod(ToBase58Check.class, false);
    }

    @Test
    public void hasDeriveExtendedPublicKey() {
        assertHasMethod(DeriveExtendedPublicKey.class, true);
    }

    @Test
    public void hasExtractPublicKeyFromExtendedPublicKey() {
        assertHasMethod(ExtractPublicKeyFromExtendedPublicKey.class, true);
    }

    @Test
    public void hasGetMultisigScriptHash() {
        assertHasMethod(GetMultisigScriptHash.class, false);
    }

    private void assertHasMethod(Class clazz, boolean withHelper) {
        Optional<NativeMethod> method = contract.getMethods().stream()
                .filter(m -> m.getClass() == clazz).findFirst();
        Assert.assertTrue(method.isPresent());
        Assert.assertEquals(executionEnvironment, method.get().getExecutionEnvironment());
        if (withHelper) {
            Assert.assertNotNull(Whitebox.getInternalState(method.get(), "helper"));
        }
    }
}