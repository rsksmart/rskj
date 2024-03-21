package co.rsk.peg.feeperkb;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.*;
import co.rsk.peg.abi.ABICallElection;
import org.ethereum.core.Repository;

import static co.rsk.peg.BridgeStorageIndexKey.FEE_PER_KB_ELECTION_KEY;
import static co.rsk.peg.BridgeStorageIndexKey.FEE_PER_KB_KEY;

public class FeePerKbStorageProvider extends StorageAccessor {
    private Coin feePerKb;
    private ABICallElection feePerKbElection;

    public FeePerKbStorageProvider(
        Repository repository,
        RskAddress contractAddress) {
        super(repository, contractAddress);
    }

    public void setFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    public void saveFeePerKb() {
        if (feePerKb == null) {
            return;
        }

        safeSaveToRepository(FEE_PER_KB_KEY, feePerKb, BridgeSerializationUtils::serializeCoin);
    }

    public Coin getFeePerKb() {
        if (feePerKb != null) {
            return feePerKb;
        }

        feePerKb = safeGetFromRepository(FEE_PER_KB_KEY, BridgeSerializationUtils::deserializeCoin);
        return feePerKb;
    }
    public void saveFeePerKbElection() {
        if (feePerKbElection == null) {
            return;
        }

        safeSaveToRepository(FEE_PER_KB_ELECTION_KEY, feePerKbElection, BridgeSerializationUtils::serializeElection);
    }

    public ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer) {
        if (feePerKbElection != null) {
            return feePerKbElection;
        }

        feePerKbElection = safeGetFromRepository(FEE_PER_KB_ELECTION_KEY, data -> BridgeSerializationUtils.deserializeElection(data, authorizer));
        return feePerKbElection;
    }

    protected void save() {
        saveFeePerKb();
        saveFeePerKbElection();
    }
}
