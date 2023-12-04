


package co.rsk.peg;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static co.rsk.peg.FederationFormatVersion.NON_STANDARD_ERP_FEDERATION;
import static co.rsk.peg.FederationFormatVersion.P2SH_ERP_FEDERATION;

class NonStandardErpFederationContextTest {

    @ParameterizedTest
    @MethodSource("provideNonStandardContexts")
    void NonStandardContexts_return_NonStandardBuilderAndNonStandardErpFederationFormatVersion(ErpFederationContext context) {
        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
    }

    @Test
    void P2shContext_returns_P2shBuilderAndP2shErpFederationFormatVersion() {
        ErpFederationContext context = new P2shErpFederationContext();
        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(P2SH_ERP_FEDERATION.getFormatVersion(), version);
    }

    // non-standard contexts provider
    private static Stream<ErpFederationContext> provideNonStandardContexts() {
        return Stream.of(
            new NonStandardErpFederationContextHardcoded(),
            new NonStandardErpFederationContextWithCsvUnsignedBE(),
            new NonStandardErpFederationContext()
        );
    }
}
