package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ErpFederation extends Federation {
    private final List<BtcECKey> erpPubKeys;
    private final long activationDelay;

    public ErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay
    ) {
        super(members, creationTime, creationBlockNumber, btcParams);
        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
    }

    public List<BtcECKey> getErpPubKeys() {
        return Collections.unmodifiableList(erpPubKeys);
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            redeemScript = ErpFederationRedeemScriptParser.createErpRedeemScript(
                ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys()),
                ScriptBuilder.createRedeemScript(erpPubKeys.size() / 2 + 1, erpPubKeys),
                activationDelay
            );
        }

        return redeemScript;
    }

    @Override
    public Script getStandardRedeemScript() {
        return ErpFederationRedeemScriptParser.extractStandardRedeemScript(
            getRedeemScript().getChunks()
        );
    }

    @Override
    public Script getP2SHScript() {
        if (p2shScript == null) {
            p2shScript = ScriptBuilder.createP2SHOutputScript(getRedeemScript());
        }

        return p2shScript;
    }

    @Override
    public Address getAddress() {
        if (address == null) {
            address = Address.fromP2SHScript(btcParams, getP2SHScript());
        }

        return address;
    }

    @Override
    public boolean equals(Object other) {
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
