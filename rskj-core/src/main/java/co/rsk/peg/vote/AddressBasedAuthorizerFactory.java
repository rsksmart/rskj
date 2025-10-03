package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer.MinimumRequiredCalculation;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Factory methods for building AddressBasedAuthorizer instances with validated inputs.
 *
 * Best practice notes:
 * - Public factory methods perform argument validation and throw IllegalArgumentException for invalid
 *   caller-provided values rather than relying on NullPointerException. This keeps the API contract explicit
 *   and matches existing tests and behavior.
 * - ZERO_ADDRESS is considered a valid address and is allowed when constructing authorizers.
 */
public class AddressBasedAuthorizerFactory {
    private AddressBasedAuthorizerFactory() { }

    /**
     * Builds an authorizer that authorizes exactly one address.
     * <p>
     * Validation and behavior:
     * - authorizedAddress must not be null
     * - ZERO_ADDRESS is allowed and results in an authorizer that only authorizes ZERO_ADDRESS.
     * </p>
     * @param authorizedAddress the single address to authorize (may be ZERO_ADDRESS but not null)
     * @return an AddressBasedAuthorizer configured for the single address
     * @throws NullPointerException if authorizedAddress is null
     */
    public static AddressBasedAuthorizer buildSingleAuthorizer(@Nonnull RskAddress authorizedAddress) {
        Objects.requireNonNull(authorizedAddress, "Cannot build an authorizer with a null address");

        return AddressBasedAuthorizer.of(authorizedAddress);
    }

    /**
     * Builds an authorizer requiring a strict majority of the provided addresses.
     * <p>
     * Validation and behavior:
     * - authorizedAddresses must not be null
     * - authorizedAddresses must contain at least 3 addresses; less than that causes IllegalArgumentException.
     * - authorizedAddresses must not contain null elements; otherwise, IllegalArgumentException is thrown.
     * - ZERO_ADDRESS is allowed as a member of the set.
     *</p>
     * @param authorizedAddresses set of addresses participating in majority authorization (size >= 3, no nulls)
     * @return an AddressBasedAuthorizer configured to require majority approval
     * @throws NullPointerException if the set is null
     * @throws IllegalArgumentException if the set has size < 3, or contains null elements
     */
    public static AddressBasedAuthorizer buildMajorityAuthorizer(@Nonnull Set<RskAddress> authorizedAddresses) {
        Objects.requireNonNull(authorizedAddresses, "Cannot build an authorizer with a null set of authorized addresses");

        if (authorizedAddresses.size() < 3) {
            throw new IllegalArgumentException("Majority authorizer requires at least 3 authorized addresses");
        }
        boolean hasNullAddress = authorizedAddresses.stream().anyMatch(Objects::isNull);
        if (hasNullAddress) {
            throw new IllegalArgumentException("Cannot build a majority authorizer with a null address");
        }
        return AddressBasedAuthorizer.of(authorizedAddresses, MinimumRequiredCalculation.MAJORITY);
    }
}
