package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;
import co.rsk.crypto.Keccak256;

public interface FlyoverRedeemScriptBuilder {
    Script addFlyoverDerivationHashToRedeemScript(Keccak256 flyoverDerivationHash, Script redeemScript);

}
