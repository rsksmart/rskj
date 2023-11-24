package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NonStandardErpRedeemScriptBuilderHardcoded implements ErpRedeemScriptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(NonStandardErpRedeemScriptBuilderHardcoded.class);
    private static final byte[] LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES = Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955670300cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f5368ae");

    @Override
    public Script createRedeemScriptFromKeys(List<BtcECKey> defaultPublicKeys,
                                     int defaultThreshold,
                                     List<BtcECKey> emergencyPublicKeys,
                                     int emergencyThreshold,
                                     long csvValue) {

        logger.debug("[getRedeemScript] Returning hardcoded redeem script");
        return new Script(LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES);
    }
}
