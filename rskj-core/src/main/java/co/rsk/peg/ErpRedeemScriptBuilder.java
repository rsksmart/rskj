package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;

import java.util.List;

public interface ErpRedeemScriptBuilder {
    long MAX_CSV_VALUE = 65535L;

    Script createRedeemScript(List<BtcECKey> defaultPublicKeys,
                              List<BtcECKey> emergencyPublicKeys,
                              long csvValue);
}