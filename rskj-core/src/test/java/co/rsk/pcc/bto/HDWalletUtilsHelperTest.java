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

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HDWalletUtilsHelperTest {
    private HDWalletUtilsHelper helper;

    @BeforeEach
    void createHelper() {
        helper = new HDWalletUtilsHelper();
    }

    @Test
    void validateAndExtractNetworkFromExtendedPublicKeyMainnet() throws NativeContractIllegalArgumentException {
        assertEquals(
                NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
                helper.validateAndExtractNetworkFromExtendedPublicKey("xpubSomethingSomething")
        );
    }

    @Test
    void validateAndExtractNetworkFromExtendedPublicKeyTestnet() throws NativeContractIllegalArgumentException {
        assertEquals(
                NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
                helper.validateAndExtractNetworkFromExtendedPublicKey("tpubSomethingSomething")
        );
    }

    @Test
    void validateAndExtractNetworkFromExtendedPublicKeyWithInvalidXpub() {
        NativeContractIllegalArgumentException exception = Assertions.assertThrows(
                NativeContractIllegalArgumentException.class,
                () -> helper.validateAndExtractNetworkFromExtendedPublicKey("completelyInvalidStuff"));
        assertEquals("Invalid extended public key 'completelyInvalidStuff'", exception.getMessage());
    }

    @Test
    void validateAndExtractNetworkFromExtendedPublicKeyWithInvalidLongXpub() {
        NativeContractIllegalArgumentException exception = Assertions.assertThrows(
                NativeContractIllegalArgumentException.class,
                () -> helper.validateAndExtractNetworkFromExtendedPublicKey("completelyInvalidLongLongLongLongLongLongLongLongLongLongLonStuff"));
        assertEquals("Invalid extended public key 'completelyInvalidLongLongLongLongLongLongLongLongLongLongLonStuf...'", exception.getMessage());
    }
}
