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
    public boolean equals(Object other){
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        ErpFederation otherErpFederation = (ErpFederation) other;
        return this.getNumberOfSignaturesRequired() == otherErpFederation.getNumberOfSignaturesRequired() &&
            this.getSize() == otherErpFederation.getSize() &&
            this.getCreationTime().equals(otherErpFederation.getCreationTime()) &&
            this.creationBlockNumber == otherErpFederation.creationBlockNumber &&
            this.btcParams.equals(otherErpFederation.btcParams) &&
            this.members.equals(otherErpFederation.members) &&
            this.getRedeemScript().equals(otherErpFederation.getRedeemScript()) &&
            this.erpPubKeys.equals(otherErpFederation.erpPubKeys) &&
            this.activationDelay == otherErpFederation.activationDelay;
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
