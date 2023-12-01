package co.rsk.peg;

import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;

public interface ErpFederationContext {
    ErpRedeemScriptBuilder getRedeemScriptBuilder();
    int getFederationFormatVersion();
}
