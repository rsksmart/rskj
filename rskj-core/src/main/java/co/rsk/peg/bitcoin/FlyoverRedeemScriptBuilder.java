package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;

public interface FlyoverRedeemScriptBuilder {
    Script addFlyoverDerivationHashToRedeemScript(Sha256Hash flyoverDerivationHash, Script redeemScript);

}
