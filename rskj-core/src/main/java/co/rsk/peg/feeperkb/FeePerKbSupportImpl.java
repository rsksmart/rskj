package co.rsk.peg.feeperkb;

import co.rsk.peg.vote.*;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.feeperkb.constants.*;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class FeePerKbSupportImpl implements FeePerKbSupport {

    private static final Logger logger = LoggerFactory.getLogger(FeePerKbSupportImpl.class);
    private static final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";

    private final FeePerKbStorageProvider storageProvider;
    private final FeePerKbConstants constants;

    public FeePerKbSupportImpl(FeePerKbConstants constants, FeePerKbStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
        this.constants = constants;
    }

    @Override
    public Coin getFeePerKb() {
        Optional<Coin> currentFeePerKb = storageProvider.getFeePerKb();

        return currentFeePerKb.orElseGet(constants::getGenesisFeePerKb);
    }

    @Override
    public Integer voteFeePerKbChange(Transaction tx, Coin feePerKb, SignatureCache signatureCache) {

        logger.info("[voteFeePerKbChange] Voting new fee per kb value: {}", feePerKb);

        AddressBasedAuthorizer authorizer = constants.getFeePerKbChangeAuthorizer();
        Coin maxFeePerKb = constants.getMaxFeePerKb();

        if (!authorizer.isAuthorized(tx, signatureCache)) {
            logger.warn("[voteFeePerKbChange] Unauthorized signature.");
            return FeePerKbResponseCode.UNAUTHORIZED_CALLER.getCode();
        }

        if (!feePerKb.isPositive()){
            logger.warn("[voteFeePerKbChange] Negative fee.");
            return FeePerKbResponseCode.NEGATIVE_FEE_VOTED.getCode();
        }

        if (feePerKb.isGreaterThan(maxFeePerKb)) {
            logger.warn("[voteFeePerKbChange] Fee greater than maximum.");
            return FeePerKbResponseCode.EXCESSIVE_FEE_VOTED.getCode();
        }

        ABICallElection feePerKbElection = storageProvider.getFeePerKbElection(authorizer);
        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        boolean successfulVote = feePerKbElection.vote(feeVote, tx.getSender(signatureCache));
        if (!successfulVote) {
            logger.warn("[voteFeePerKbChange] Unsuccessful {} vote", feeVote);
            return FeePerKbResponseCode.UNSUCCESSFUL_VOTE.getCode();
        }

        Optional<ABICallSpec> winnerOptional = feePerKbElection.getWinner();
        if (!winnerOptional.isPresent()) {
            logger.info("[voteFeePerKbChange] Successful fee per kb vote for {}", feePerKb);
            return FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        }

        ABICallSpec winner = winnerOptional.get();
        Coin winnerFee;
        try {
            winnerFee = BridgeSerializationUtils.deserializeCoin(winner.getArguments()[0]);
        } catch (Exception e) {
            logger.warn("[voteFeePerKbChange] Exception deserializing winner feePerKb", e);
            return FeePerKbResponseCode.GENERIC_ERROR.getCode();
        }

        if (winnerFee == null) {
            logger.warn("[voteFeePerKbChange] Invalid winner feePerKb: feePerKb can't be null");
            return FeePerKbResponseCode.GENERIC_ERROR.getCode();
        }

        if (!winnerFee.equals(feePerKb)) {
            logger.debug("[voteFeePerKbChange] Winner fee is different than the last vote: maybe you forgot to clear winners");
        }

        logger.info("[voteFeePerKbChange] Fee per kb changed to {}", winnerFee);
        storageProvider.setFeePerKb(winnerFee);
        feePerKbElection.clear();

        return FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
    }

    @Override
    public void save() {
        storageProvider.save();
    }
}
