package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer.MinimumRequiredCalculation;
import java.util.Objects;
import java.util.Set;

public class AddressBasedAuthorizerFactory {
    private AddressBasedAuthorizerFactory() { }

    public static AddressBasedAuthorizer buildSingleAuthorizer(RskAddress authorizedAddress) {
        if (authorizedAddress == null) {
            throw new IllegalArgumentException("Cannot build an authorizer with a null address");
        }
        return AddressBasedAuthorizer.of(authorizedAddress);
    }

    public static AddressBasedAuthorizer buildMajorityAuthorizer(Set<RskAddress> authorizedAddresses) {
        if(authorizedAddresses == null) {
            throw new IllegalArgumentException("Cannot build an authorizer with a null set of authorized addresses");
        }
        if (authorizedAddresses.isEmpty()) {
            throw new IllegalArgumentException("Cannot build an authorizer with no authorized addresses");
        }
        if (authorizedAddresses.size() == 1) {
            throw new IllegalArgumentException("Cannot build a majority authorizer with a single authorized address");
        }
        boolean hasNullAddress = authorizedAddresses.stream().anyMatch(Objects::isNull);
        if (hasNullAddress) {
            throw new IllegalArgumentException("Cannot build a majority authorizer with a null address");
        }
        return AddressBasedAuthorizer.of(authorizedAddresses, MinimumRequiredCalculation.MAJORITY);
    }
}
