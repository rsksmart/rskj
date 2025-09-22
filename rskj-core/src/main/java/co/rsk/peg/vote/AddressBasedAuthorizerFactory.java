package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer.MinimumRequiredCalculation;
import java.util.Set;

public class AddressBasedAuthorizerFactory {
    private AddressBasedAuthorizerFactory() { }

    public static AddressBasedAuthorizer buildSingleAuthorizer(RskAddress authorizedAddress) {
        return AddressBasedAuthorizer.of(authorizedAddress);
    }

    public static AddressBasedAuthorizer buildMajorityAuthorizer(Set<RskAddress> authorizedAddresses) {
        return AddressBasedAuthorizer.of(authorizedAddresses, MinimumRequiredCalculation.MAJORITY);
    }
}
