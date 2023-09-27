package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ErpFederation extends Federation {
    private static final Logger logger = LoggerFactory.getLogger(ErpFederation.class);

    protected static final byte[] ERP_TESTNET_REDEEM_SCRIPT_BYTES = Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955670300cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f5368ae");

    protected final List<BtcECKey> erpPubKeys;
    protected final long activationDelay;
    protected final ActivationConfig.ForBlock activations;
    protected Script standardRedeemScript;
    // add erpRedeemScript? is it necessary?
    protected Script standardP2SHScript;
    // add erpP2SHScript?

    public ErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations) {

        super(members, creationTime, creationBlockNumber, btcParams);
        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
        this.activations = activations;

        // Try getting the redeem script in order to validate it can be built
        // using the given public keys and csv value
        getRedeemScript(); // NOSONAR
        validateRedeemScript();
        getStandardRedeemScript(); // NOSONAR
    }

    public List<BtcECKey> getErpPubKeys() {
        return Collections.unmodifiableList(erpPubKeys);
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    public abstract Script getStandardRedeemScript();

    public Script getStandardP2SHScript() {
        if (standardP2SHScript == null) {
            standardP2SHScript = ScriptBuilder.createP2SHOutputScript(getStandardRedeemScript());
        }

        return standardP2SHScript;
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since all of Instant, int and List<BtcECKey> have
        // well-defined hashCode()s
        return Objects.hash(
            getCreationTime(),
            this.creationBlockNumber,
            getNumberOfSignaturesRequired(),
            getBtcPublicKeys(),
            getErpPubKeys(),
            getActivationDelay()
        );
    }

    private void validateRedeemScript() {
        if (activations.isActive(ConsensusRule.RSKIP293) &&
            this.redeemScript.equals(new Script(ERP_TESTNET_REDEEM_SCRIPT_BYTES))) {

            String message = "Unable to create ERP Federation. The obtained redeem script matches the one hardcoded for testnet. "
                + "This would cause bitcoinj-thin to identify it as invalid";
            logger.debug("[validateRedeemScript] {}", message);
            throw new FederationCreationException(message);
        }
    }
}
