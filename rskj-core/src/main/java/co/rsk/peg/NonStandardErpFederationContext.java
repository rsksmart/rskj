package co.rsk.peg;

import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;

import static co.rsk.peg.FederationFormatVersion.NON_STANDARD_ERP_FEDERATION;

public class NonStandardErpFederationContext implements ErpFederationContext {

    private final ErpRedeemScriptBuilder builder;

    public NonStandardErpFederationContext(ErpRedeemScriptBuilder builder) {
        this.builder = builder;
    }
    @Override
    public ErpRedeemScriptBuilder getRedeemScriptBuilder() {
        return builder;
    }

    @Override
    public int getFederationFormatVersion() {
        return NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    }
}
