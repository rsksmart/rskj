package co.rsk.peg;

import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderHardcoded;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static co.rsk.peg.FederationFormatVersion.NON_STANDARD_ERP_FEDERATION;
import static co.rsk.peg.FederationFormatVersion.P2SH_ERP_FEDERATION;

class NonStandardErpFederationContextTest {

    @ParameterizedTest
    @MethodSource("provideNonStandardBuilders")
    void NonStandardBuilders_return_NonStandardErpFederationFormatVersion(ErpRedeemScriptBuilder builder) {
        ErpFederationContext context = new NonStandardErpFederationContext(builder);
        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(NON_STANDARD_ERP_FEDERATION.getFormatVersion(), version);
    }

    @Test
    void P2shContext_returns_P2shBuilderAndP2shErpFederationFormatVersion() {
        ErpFederationContext context = new P2shErpFederationContext();
        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(P2SH_ERP_FEDERATION.getFormatVersion(), version);
    }

    // non-standard builders provider
    private static Stream<ErpRedeemScriptBuilder> provideNonStandardBuilders() {
        return Stream.of(
            new NonStandardErpRedeemScriptBuilderHardcoded(),
            new NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE(),
            new NonStandardErpRedeemScriptBuilder()
        );
    }
}
