package co.rsk.peg.feeperkb;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.BridgeStorageAccessor;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;

import static co.rsk.peg.storage.FeePerKbStorageIndexKey.FEE_PER_KB_ELECTION;
import static co.rsk.peg.storage.FeePerKbStorageIndexKey.FEE_PER_KB;

public class FeePerKbStorageProvider {
    private final BridgeStorageAccessor bridgeStorageAccessor;
    private Coin feePerKb;
    private ABICallElection feePerKbElection;

    public FeePerKbStorageProvider(BridgeStorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    protected void setFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private void saveFeePerKb() {
        if (feePerKb == null) {
            return;
        }

        bridgeStorageAccessor.safeSaveToRepository(FEE_PER_KB.getKey(), feePerKb, BridgeSerializationUtils::serializeCoin);
    }

    protected Coin getFeePerKb() {
        if (feePerKb != null) {
            return feePerKb;
        }

        feePerKb = bridgeStorageAccessor.safeGetFromRepository(FEE_PER_KB.getKey(), BridgeSerializationUtils::deserializeCoin);
        return feePerKb;
    }

    private void saveFeePerKbElection() {
        if (feePerKbElection == null) {
            return;
        }

        bridgeStorageAccessor.safeSaveToRepository(FEE_PER_KB_ELECTION.getKey(), feePerKbElection, BridgeSerializationUtils::serializeElection);
    }

    protected ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer) {
        if (feePerKbElection != null) {
            return feePerKbElection;
        }

        feePerKbElection = bridgeStorageAccessor.safeGetFromRepository(FEE_PER_KB_ELECTION.getKey(), data -> BridgeSerializationUtils.deserializeElection(data, authorizer));
        return feePerKbElection;
    }

    protected void save() {
        saveFeePerKb();
        saveFeePerKbElection();
    }
}
