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
package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import org.ethereum.crypto.ECKey;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory to create {@link AddressBasedAuthorizer} instances without exposing constructors.
 */
public final class AddressBasedAuthorizerFactory {
    private AddressBasedAuthorizerFactory() { }

    // Smart contract case: single address, default minimum is ONE
    public static AddressBasedAuthorizer fromAddress(RskAddress contractAddress) {
        return AddressBasedAuthorizer.of(contractAddress);
    }

    // Multiple addresses (if applicable), with configurable calculation
    public static AddressBasedAuthorizer fromAddresses(List<RskAddress> addresses,
                                                       AddressBasedAuthorizer.MinimumRequiredCalculation calculation) {
        return AddressBasedAuthorizer.of(addresses, calculation);
    }

    // Compatibility path: from ECKey list
    public static AddressBasedAuthorizer fromKeys(List<ECKey> keys, AddressBasedAuthorizer.MinimumRequiredCalculation calculation) {
        List<RskAddress> addresses = keys.stream()
                .map(key -> new RskAddress(key.getAddress()))
                .collect(Collectors.toList());
        return AddressBasedAuthorizer.of(addresses, calculation);
    }

    // Single legacy key -> minimum ONE
    public static AddressBasedAuthorizer fromKey(ECKey key) {
        return fromKeys(Collections.singletonList(key), AddressBasedAuthorizer.MinimumRequiredCalculation.ONE);
    }
}
