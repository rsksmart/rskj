/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.crypto.signature;

import co.rsk.config.RskSystemProperties;
import org.bitcoin.Secp256k1Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Secp256k1Context.class)
public class Secp256k1Test {

    @Before
    public void init() {
        // Lets assume we have the ability to run Native Library.
        PowerMockito.mockStatic(Secp256k1Context.class);
        PowerMockito.when(Secp256k1Context.isEnabled()).thenReturn(Boolean.TRUE);
    }

    @Test
    public void testInitialization_notInitialized() {
        Secp256k1.reset();
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceBC);
        assertFalse(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);
    }

    @Test
    public void testInitialization_BC() {
        // Test BC init
        Secp256k1.reset();
        RskSystemProperties properties = Mockito.mock(RskSystemProperties.class);
        Mockito.when(properties.cryptoLibrary()).thenReturn("bc");
        Secp256k1.initialize(properties);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceBC);
        assertFalse(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);
    }

    @Test
    public void testInitialization_Native() {
        // Test Native init
        Secp256k1.reset();
        RskSystemProperties properties = Mockito.mock(RskSystemProperties.class);
        Mockito.when(properties.cryptoLibrary()).thenReturn("native");
        Secp256k1.initialize(properties);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);
    }


    @Test
    public void testInitialization_NullProperties() {
        // Test Native init
        Secp256k1.reset();
        Secp256k1.initialize(null);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceBC);
        assertFalse(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);

        RskSystemProperties properties = Mockito.mock(RskSystemProperties.class);
        Mockito.when(properties.cryptoLibrary()).thenReturn("native");
        Secp256k1.initialize(properties);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);

        Secp256k1.initialize(null);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);
    }

    @Test
    public void testInitialization_Twice() {
        // Test Native init
        Secp256k1.reset();
        RskSystemProperties properties = Mockito.mock(RskSystemProperties.class);
        Mockito.when(properties.cryptoLibrary()).thenReturn("native");
        Secp256k1.initialize(properties);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);
        Mockito.when(properties.cryptoLibrary()).thenReturn("bc");
        Secp256k1.initialize(properties);
        assertTrue(Secp256k1.getInstance() instanceof Secp256k1ServiceBC);
        assertFalse(Secp256k1.getInstance() instanceof Secp256k1ServiceNative);
    }

}
