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
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Authorizes an operation based
 * on an RSK address.
 *
 * @author Ariel Mendelzon
 */
public class AddressBasedAuthorizer {
    public enum MinimumRequiredCalculation { ONE, MAJORITY, ALL }

    protected final List<byte[]> authorizedAddresses;
    protected final MinimumRequiredCalculation requiredCalculation;

    // New private constructor that accepts addresses directly
    private AddressBasedAuthorizer(java.util.Collection<RskAddress> authorizedAddresses, MinimumRequiredCalculation requiredCalculation) {
        this.authorizedAddresses = Objects.requireNonNull(authorizedAddresses, "authorizedAddresses")
            .stream()
            .map(addr -> Objects.requireNonNull(addr, "address").getBytes())
            .collect(Collectors.toList());
        this.requiredCalculation = Objects.requireNonNull(requiredCalculation, "requiredCalculation");
    }

    /**
     * @deprecated Use AddressBasedAuthorizerFactory to construct instances.
     */
    @Deprecated
    public AddressBasedAuthorizer(List<ECKey> authorizedKeys, MinimumRequiredCalculation requiredCalculation) {
        this(
            Objects.requireNonNull(authorizedKeys, "authorizedKeys").stream()
                .map(k -> new RskAddress(k.getAddress()))
                .collect(Collectors.toList()),
            requiredCalculation
        );
    }

    public boolean isAuthorized(RskAddress sender) {
        if (sender == null) return false;
        byte[] senderBytes = sender.getBytes();
        return authorizedAddresses.stream()
            .anyMatch(address -> Arrays.equals(address, senderBytes));
    }

    public boolean isAuthorized(Transaction tx, SignatureCache signatureCache) {
        if (tx == null) return false;
        return isAuthorized(tx.getSender(signatureCache));
    }

    // backward compatibility
    @Deprecated
    public int getNumberOfAuthorizedKeys() {
        return authorizedAddresses.size();
    }

    public int getNumberOfAuthorizedAddresses() {
        return authorizedAddresses.size();
    }

    // backward compatibility
    @Deprecated
    public int getRequiredAuthorizedKeys() {
        return getRequiredAuthorizedAddresses();
    }

    public int getRequiredAuthorizedAddresses() {
        return switch (requiredCalculation) {
            case ONE -> 1;
            case MAJORITY -> getNumberOfAuthorizedAddresses() / 2 + 1;
            default -> getNumberOfAuthorizedAddresses();
        };
    }

    static AddressBasedAuthorizer of(List<RskAddress> authorizedAddresses, MinimumRequiredCalculation calculation) {
        return new AddressBasedAuthorizer(authorizedAddresses, calculation);
    }

    static AddressBasedAuthorizer of(RskAddress address) {
        return new AddressBasedAuthorizer(Collections.singletonList(address), MinimumRequiredCalculation.ONE);
    }
}
