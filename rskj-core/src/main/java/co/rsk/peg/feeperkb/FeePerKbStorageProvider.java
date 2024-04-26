package co.rsk.peg.feeperkb;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;

import java.util.Optional;

public interface FeePerKbStorageProvider {

    void setFeePerKb(Coin feePerKb);

    Optional<Coin> getFeePerKb();

    ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer);

    void save();

}
