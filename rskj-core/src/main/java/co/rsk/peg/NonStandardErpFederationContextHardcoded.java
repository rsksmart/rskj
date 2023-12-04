package co.rsk.peg;

import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderHardcoded;

import static co.rsk.peg.FederationFormatVersion.NON_STANDARD_ERP_FEDERATION;

public class NonStandardErpFederationContextHardcoded implements ErpFederationContext {
    @Override
    public ErpRedeemScriptBuilder getRedeemScriptBuilder() {
        return new NonStandardErpRedeemScriptBuilderHardcoded();
    }

    @Override
    public int getFederationFormatVersion() {
        return NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    }
}
