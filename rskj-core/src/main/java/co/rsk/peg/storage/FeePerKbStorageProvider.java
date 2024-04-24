package co.rsk.peg.storage;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;

import static co.rsk.peg.storage.FeePerKbStorageIndexKey.FEE_PER_KB_ELECTION_KEY;
import static co.rsk.peg.storage.FeePerKbStorageIndexKey.FEE_PER_KB_KEY;

public class FeePerKbStorageProvider {
    private final BridgeStorageAccessor bridgeStorageAccessor;
    private Coin feePerKb;
    private ABICallElection feePerKbElection;

    public FeePerKbStorageProvider(BridgeStorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    public void setFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    public void saveFeePerKb() {
        if (feePerKb == null) {
            return;
        }

        bridgeStorageAccessor.safeSaveToRepository(FEE_PER_KB_KEY.getKey(), feePerKb, BridgeSerializationUtils::serializeCoin);
    }

    public Coin getFeePerKb() {
        if (feePerKb != null) {
            return feePerKb;
        }

        feePerKb = bridgeStorageAccessor.safeGetFromRepository(FEE_PER_KB_KEY.getKey(), BridgeSerializationUtils::deserializeCoin);
        return feePerKb;
    }
    public void saveFeePerKbElection() {
        if (feePerKbElection == null) {
            return;
        }

        bridgeStorageAccessor.safeSaveToRepository(FEE_PER_KB_ELECTION_KEY.getKey(), feePerKbElection, BridgeSerializationUtils::serializeElection);
    }

    public ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer) {
        if (feePerKbElection != null) {
            return feePerKbElection;
        }

        feePerKbElection = bridgeStorageAccessor.safeGetFromRepository(FEE_PER_KB_ELECTION_KEY.getKey(), data -> BridgeSerializationUtils.deserializeElection(data, authorizer));
        return feePerKbElection;
    }

    public void save() {
        saveFeePerKb();
        saveFeePerKbElection();
    }
}
