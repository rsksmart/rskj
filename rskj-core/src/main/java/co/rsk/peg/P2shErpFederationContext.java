package co.rsk.peg;

import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.P2shErpRedeemScriptBuilder;

import static co.rsk.peg.FederationFormatVersion.P2SH_ERP_FEDERATION;

public class P2shErpFederationContext implements ErpFederationContext {
    @Override
    public ErpRedeemScriptBuilder getRedeemScriptBuilder() {
        return new P2shErpRedeemScriptBuilder();
    }

    @Override
    public int getFederationFormatVersion() {
        return P2SH_ERP_FEDERATION.getFormatVersion();
    }
}
