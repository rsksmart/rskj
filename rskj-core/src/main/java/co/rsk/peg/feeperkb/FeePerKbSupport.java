package co.rsk.peg.feeperkb;

import co.rsk.peg.storage.FeePerKbStorageProvider;
import co.rsk.peg.vote.*;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.feeperkb.constants.*;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class FeePerKbSupport {

    private final FeePerKbStorageProvider provider;
    private final FeePerKbConstants feePerKbConstants;
    private static final Logger logger = LoggerFactory.getLogger(FeePerKbSupport.class);
    private static final String setFeePerKbAbiFunction = "setFeePerKb";

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
     * @return
     * UNAUTHORIZED fee per kb response code when the signature is not authorized to vote.
     * NEGATIVE fee per kb response code when fee is not positive.
     * EXCESSIVE fee per kb response code when fee is greater than the maximum fee allowed.
     * UNSUCCESSFUL fee per kb response code when the vote was unsuccessful.
     * GENERIC fee per kb response code when there was an unexpected error.
     * SUCCESSFUL fee per kb response code when the vote was successful.
     */
    public Integer voteFeePerKbChange(Transaction tx, Coin feePerKb, SignatureCache signatureCache) {

        logger.info("[voteFeePerKbChange] Voting new fee per kb value: {}", feePerKb);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin maxFeePerKb = feePerKbConstants.getMaxFeePerKb();

        if (!authorizer.isAuthorized(tx, signatureCache)) {
            logger.warn("[voteFeePerKbChange] Unauthorized signature.");
            return FeePerKbResponseCode.UNAUTHORIZED.getCode();
        }

        if (!feePerKb.isPositive()){
            logger.warn("[voteFeePerKbChange] Negative fee.");
            return FeePerKbResponseCode.NEGATIVE.getCode();
        }

        if (feePerKb.isGreaterThan(maxFeePerKb)) {
            logger.warn("[voteFeePerKbChange] Fee greater than maximum.");
            return FeePerKbResponseCode.EXCESSIVE.getCode();
        }

        ABICallElection feePerKbElection = provider.getFeePerKbElection(authorizer);
        ABICallSpec feeVote = new ABICallSpec(setFeePerKbAbiFunction, new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        boolean successfulVote = feePerKbElection.vote(feeVote, tx.getSender(signatureCache));
        if (!successfulVote) {
            logger.warn("[voteFeePerKbChange] Unsuccessful {} vote", feeVote);
            return FeePerKbResponseCode.UNSUCCESSFUL.getCode();
        }

        Optional<ABICallSpec> winnerOptional = feePerKbElection.getWinner();
        if (!winnerOptional.isPresent()) {
            logger.info("[voteFeePerKbChange] Successful fee per kb vote for {}", feePerKb);
            return FeePerKbResponseCode.SUCCESSFUL.getCode();
        }

        ABICallSpec winner = winnerOptional.get();
        Coin winnerFee;
        try {
            winnerFee = BridgeSerializationUtils.deserializeCoin(winner.getArguments()[0]);
        } catch (Exception e) {
            logger.warn("[voteFeePerKbChange] Exception deserializing winner feePerKb", e);
            return FeePerKbResponseCode.GENERIC.getCode();
        }

        if (winnerFee == null) {
            logger.warn("[voteFeePerKbChange] Invalid winner feePerKb: feePerKb can't be null");
            return FeePerKbResponseCode.GENERIC.getCode();
        }

        if (!winnerFee.equals(feePerKb)) {
            logger.debug("[voteFeePerKbChange] Winner fee is different than the last vote: maybe you forgot to clear winners");
        }

        logger.info("[voteFeePerKbChange] Fee per kb changed to {}", winnerFee);
        provider.setFeePerKb(winnerFee);
        feePerKbElection.clear();

        return FeePerKbResponseCode.SUCCESSFUL.getCode();
    }

    public void save() {
        provider.save();
    }
}
