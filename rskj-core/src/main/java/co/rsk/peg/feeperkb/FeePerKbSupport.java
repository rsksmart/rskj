package co.rsk.peg.feeperkb;

import co.rsk.peg.abi.*;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.AddressBasedAuthorizer;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.feeperkb.constants.*;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeePerKbSupport {

    private final FeePerKbStorageProvider provider;
    private final FeePerKbConstants feePerKbConstants;
    private static final Logger logger = LoggerFactory.getLogger(FeePerKbSupport.class);

    public FeePerKbSupport(FeePerKbConstants feePerKbConstants, FeePerKbStorageProvider provider) {
        this.provider = provider;
        this.feePerKbConstants = feePerKbConstants;
    }

    /**
     * @return Current fee per kb in BTC.
     */
    public Coin getFeePerKb() {
        Coin currentFeePerKb = provider.getFeePerKb();

        if (currentFeePerKb == null) {
            currentFeePerKb = feePerKbConstants.getGenesisFeePerKb();
        }

        return currentFeePerKb;
    }

    /**
     * Votes for a fee per kb value.
     *
     * @return 1 upon successful vote, -1 when the vote was unsuccessful,
     * FEE_PER_KB_GENERIC_ERROR_CODE when there was an un expected error.
     */
    public Integer voteFeePerKbChange(Transaction tx, Coin feePerKb, SignatureCache signatureCache) {

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin maxFeePerKb = feePerKbConstants.getMaxFeePerKb();

        if (!authorizer.isAuthorized(tx, signatureCache)) {
            logger.warn("[voteFeePerKbChange] Unauthorized signature.");
            return FeePerKbErrorCode.GENERIC.getCode();
        }

        if (!feePerKb.isPositive()){
            logger.warn("[voteFeePerKbChange] Negative fee.");
            return FeePerKbErrorCode.NEGATIVE.getCode();
        }

        if (feePerKb.isGreaterThan(maxFeePerKb)) {
            logger.warn("[voteFeePerKbChange] Fee greater than maximum.");
            return FeePerKbErrorCode.EXCESSIVE.getCode();
        }

        ABICallElection feePerKbElection = provider.getFeePerKbElection(authorizer);
        ABICallSpec feeVote = new ABICallSpec("setFeePerKb", new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        boolean successfulVote = feePerKbElection.vote(feeVote, tx.getSender(signatureCache));
        if (!successfulVote) {
            return -1;
        }

        ABICallSpec winner = feePerKbElection.getWinner();
        if (winner == null) {
            logger.info("[voteFeePerKbChange] Successful fee per kb vote for {}", feePerKb);
            return 1;
        }

        Coin winnerFee;
        try {
            winnerFee = BridgeSerializationUtils.deserializeCoin(winner.getArguments()[0]);
        } catch (Exception e) {
            logger.warn("[voteFeePerKbChange] Exception deserializing winner feePerKb", e);
            return FeePerKbErrorCode.GENERIC.getCode();
        }

        if (winnerFee == null) {
            logger.warn("[voteFeePerKbChange] Invalid winner feePerKb: feePerKb can't be null");
            return FeePerKbErrorCode.GENERIC.getCode();
        }

        if (!winnerFee.equals(feePerKb)) {
            logger.debug("[voteFeePerKbChange] Winner fee is different than the last vote: maybe you forgot to clear winners");
        }

        logger.info("[voteFeePerKbChange] Fee per kb changed to {}", winnerFee);
        provider.setFeePerKb(winnerFee);
        feePerKbElection.clear();

        return 1;
    }

    public void save() {
        provider.save();
    }
}
