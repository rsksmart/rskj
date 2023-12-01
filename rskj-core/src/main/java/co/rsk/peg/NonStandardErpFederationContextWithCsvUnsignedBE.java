package co.rsk.peg;

import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE;

import static co.rsk.peg.FederationFormatVersion.NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION;

public class NonStandardErpFederationContextWithCsvUnsignedBE implements ErpFederationContext {
    @Override
    public ErpRedeemScriptBuilder getRedeemScriptBuilder() {
        return new NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE();
    }

    @Override
    public int getFederationFormatVersion() {
        return NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION;
    }
}
