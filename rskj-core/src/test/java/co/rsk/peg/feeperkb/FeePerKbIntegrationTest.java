package co.rsk.peg.feeperkb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.storage.StorageAccessor;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeePerKbIntegrationTest {
    private final FeePerKbConstants feePerKbConstants = FeePerKbMainNetConstants.getInstance();
    private FeePerKbSupport feePerKbSupport;
    private SignatureCache signatureCache;

    @BeforeEach
    void setUp() {
        StorageAccessor inMemoryStorage = new InMemoryStorage();
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(inMemoryStorage);
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
        signatureCache = mock(SignatureCache.class);
    }

    @Test
    void feePerKbIntegrationTest() {
        Coin genesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();

        // Get fee per kb before any voting, should return the genesis fee per kb
        assertFeePerKbValue(genesisFeePerKb);

        /*
         *  First voting, authorizer 1 and 2 vote the same value
         */
        Coin firstFeePerKbVote = Coin.valueOf(50_000L);
        // Send 1 vote from an authorizer
        voteFeePerKb(firstFeePerKbVote, 0);

        // Get fee per kb, shouldn't have changed because only vote was added
        assertFeePerKbValue(genesisFeePerKb);

        // Add a second vote from a different authorizer
        voteFeePerKb(firstFeePerKbVote, 1);

        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(firstFeePerKbVote);

        /*
         *  Second voting, authorizers 2 and 3 vote the same value
         */
        Coin secondFeePerKbVote = Coin.valueOf(100_000L);
        // First vote for a new value
        voteFeePerKb(secondFeePerKbVote, 2);

        // Get fee per kb, should return the first value voted because the new value has only 1 vote
        assertFeePerKbValue(firstFeePerKbVote);

        // Add a second vote for the same value, different authorizer
        voteFeePerKb(secondFeePerKbVote, 1);

        // Get fee per kb, should return the second vote value
        assertFeePerKbValue(secondFeePerKbVote);

        /*
         *  Third voting, authorizers 1 and 2 vote different values. Then authorizer 3 votes the same value as authorizer 1
         */
        Coin thirdFeePerKbVote = Coin.valueOf(40_000L);
        // First vote for a new value
        voteFeePerKb(thirdFeePerKbVote, 0);

        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(secondFeePerKbVote);

        // Add a second vote for a different value, different authorizer
        Coin differentFeePerKbVote = Coin.valueOf(60_000L);
        voteFeePerKb(differentFeePerKbVote, 1);

        // Get fee per kb, should return the second voted value because the third value has only 2 different votes
        assertFeePerKbValue(secondFeePerKbVote);

        // Add a third vote from authorizer 3, same value as the one voted from authorizer 1
        voteFeePerKb(thirdFeePerKbVote, 2);

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(thirdFeePerKbVote);

        /*
         *  Fourth voting, authorizers 1 and 2 vote the same value. Then authorizer 3 votes a different value
         */
        Coin fourthFeePerKbVote = Coin.valueOf(70_000L);
    }

    private void assertFeePerKbValue(Coin expectedValue) {
        Coin feePerKb = feePerKbSupport.getFeePerKb();
        assertEquals(expectedValue, feePerKb);
    }

    private void voteFeePerKb(Coin value, int authorizerIndex) {
        RskAddress authorizerAddress = this.getAuthorizedRskAddresses().get(authorizerIndex);
        Transaction voteTx = getTransactionFromAuthorizedCaller(authorizerAddress);
        feePerKbSupport.voteFeePerKbChange(voteTx, value, signatureCache);
    }




//    @Test
//    void getFeePerKbBeforeAnyVotes_shouldReturnGenesisFeePerKb() {
//        Coin genesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();
//
//        // Get fee per kb before any voting, should return the genesis fee per kb
//        Coin feePerKb = feePerKbSupport.getFeePerKb();
//        assertEquals(genesisFeePerKb, feePerKb);
//    }
//
//    @Test
//    void testFeePerKb_authorizers1and2_sameVote_updateFeePerKb() {
//        // Send 1 vote from an authorizer
//        Transaction firstTx = getTransactionFromAuthorizedCaller(signatureCache, 1);
//        Coin firstFeePerKbVote = Coin.valueOf(50_000L);
//        feePerKbSupport.voteFeePerKbChange(firstTx, firstFeePerKbVote, signatureCache);
//
//        // Get fee per kb, shouldn't have changed because only vote was added
//        feePerKb = feePerKbSupport.getFeePerKb();
//        assertEquals(genesisFeePerKb, feePerKb);
//
//        // Add a second vote from a different authorizer
//        Transaction secondTx = getTransactionFromAuthorizedCaller(signatureCache, 2);
//        feePerKbSupport.voteFeePerKbChange(secondTx, firstFeePerKbVote, signatureCache);
//
//        // Get fee per kb, should return the newly voted value
//        feePerKb = feePerKbSupport.getFeePerKb();
//        assertEquals(firstFeePerKbVote, feePerKb);
//    }



//    @Test
//    void voteFeePerKbChange_winnerFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
//        SignatureCache signatureCache = mock(SignatureCache.class);
//
//        //First Vote
//        Transaction firstTx = getTransactionFromAuthorizedCaller(signatureCache, 1);
//        Coin firstFeePerKbVote = Coin.valueOf(50_000L);
//        feePerKbSupport.voteFeePerKbChange(firstTx, firstFeePerKbVote, signatureCache);
//
//        //Second vote
//        Transaction secondTx = getTransactionFromAuthorizedCaller(signatureCache, 2);
//        Coin secondFeePerKbVote = Coin.valueOf(50_000L);
//        Integer ActualResult = feePerKbSupport.voteFeePerKbChange(secondTx, secondFeePerKbVote, signatureCache);
//
//        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
//        assertEquals(expectedResult, ActualResult);
//    }


    private Transaction getTransactionFromAuthorizedCaller(RskAddress authorizer) {
        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(authorizer);

        return txFromAuthorizedCaller;
    }

    private List<RskAddress> getAuthorizedRskAddresses(){
        return Stream.of(
            "a02db0ed94a5894bc6f9079bb9a2d93ada1917f3",
            "180a7edda4e640ea5a3e495e17a1efad260c39e9",
            "8418edc8fea47183116b4c8cd6a12e51a7e169c1"
        ).map(RskAddress::new).collect(Collectors.toList());
    }
}
