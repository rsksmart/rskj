package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public abstract class ErpFederation extends Federation {
    protected final List<BtcECKey> erpPubKeys;
    protected final long activationDelay;
    protected final ActivationConfig.ForBlock activations;
    protected Script standardRedeemScript;
    protected Script standardP2SHScript;

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
        getStandardRedeemScript(); // NOSONAR
    }

    public List<BtcECKey> getErpPubKeys() {
        return Collections.unmodifiableList(erpPubKeys);
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    public abstract Script getStandardRedeemScript();

    @Override
    public int getNumberOfSignaturesRequired() {
        List<ScriptChunk> standardRedeemScriptChunks = getStandardRedeemScript().getChunks();

        // the threshold of a multisig is the first chunk of the redeemScript
        // and the standardRedeemScript represents a multisig
        ScriptChunk thresholdChunk = standardRedeemScriptChunks.get(0);
        return Integer.parseInt(thresholdChunk.toString());
    }

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
}
