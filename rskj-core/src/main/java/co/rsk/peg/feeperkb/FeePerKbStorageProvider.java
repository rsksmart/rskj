package co.rsk.peg.feeperkb;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;

public interface FeePerKbStorageProvider {

    void setFeePerKb(Coin feePerKb);

    Coin getFeePerKb();

    ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer);

    void save();

}
