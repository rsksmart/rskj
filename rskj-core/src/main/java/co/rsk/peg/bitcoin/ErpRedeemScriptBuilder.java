package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;

import java.util.List;

public interface ErpRedeemScriptBuilder {

    Script createRedeemScriptFromKeys(List<BtcECKey> defaultPublicKeys,
                              int defaultThreshold,
                              List<BtcECKey> emergencyPublicKeys,
                              int emergencyThreshold,
                              long csvValue);
}
