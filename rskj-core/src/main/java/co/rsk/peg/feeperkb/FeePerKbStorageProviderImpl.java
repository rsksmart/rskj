package co.rsk.peg.feeperkb;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;

import static co.rsk.peg.storage.FeePerKbStorageIndexKey.FEE_PER_KB_ELECTION;
import static co.rsk.peg.storage.FeePerKbStorageIndexKey.FEE_PER_KB;

public class FeePerKbStorageProviderImpl implements FeePerKbStorageProvider {
    private final StorageAccessor bridgeStorageAccessor;
    private Coin feePerKb;
    private ABICallElection feePerKbElection;

    public FeePerKbStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void setFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private void saveFeePerKb() {
        if (feePerKb == null) {
            return;
        }

        bridgeStorageAccessor.safeSaveToRepository(FEE_PER_KB.getKey(), feePerKb, BridgeSerializationUtils::serializeCoin);
    }

    @Override
    public Coin getFeePerKb() {
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

    @Override
    public ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer) {
        if (feePerKbElection != null) {
            return feePerKbElection;
        }

        feePerKbElection = bridgeStorageAccessor.safeGetFromRepository(FEE_PER_KB_ELECTION.getKey(), data -> BridgeSerializationUtils.deserializeElection(data, authorizer));
        return feePerKbElection;
    }

    @Override
    public void save() {
        saveFeePerKb();
        saveFeePerKbElection();
    }
}
