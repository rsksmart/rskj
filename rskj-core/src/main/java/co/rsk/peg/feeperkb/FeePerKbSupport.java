package co.rsk.peg.feeperkb;

import co.rsk.bitcoinj.core.Coin;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;

public interface FeePerKbSupport {

    /**
     * @return Current fee per kb in BTC.
     */
    Coin getFeePerKb();

    /**
     * Votes for a fee per kb value.
     *
     * @return
     * UNAUTHORIZED fee per kb response code when the signature is not authorized to vote.
     * NEGATIVE fee per kb response code when fee is not positive.
     * EXCESSIVE fee per kb response code when fee is greater than the maximum fee allowed.
     * UNSUCCESSFUL fee per kb response code when the vote was unsuccessful.
     * GENERIC fee per kb response code when there was an unexpected error.
     * SUCCESSFUL fee per kb response code when the vote was successful.
     */
    Integer voteFeePerKbChange(Transaction tx, Coin feePerKb, SignatureCache signatureCache);


    void save();

}
