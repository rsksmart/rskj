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
        Coin differentFeePerKbVote = Coin.valueOf(60_000L);
        Coin maxFeePerKbVote = feePerKbConstants.getMaxFeePerKb();
        Coin excessiveFeePerKbVote = maxFeePerKbVote.add(Coin.SATOSHI);
        Coin genesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();

        // Get fee per kb before any voting, should return the genesis fee per kb
        assertFeePerKbValue(genesisFeePerKb);

        /*
         *  1st voting: authorizer 1 and 2 vote the same value
         */
        Coin firstFeePerKbVote = Coin.valueOf(50_000L);

        // Send 1 vote from an authorizer
        voteFeePerKb(firstFeePerKbVote, 0);
        // Get fee per kb, shouldn't have changed because only one vote was added
        assertFeePerKbValue(genesisFeePerKb);

        // Second vote from a different authorizer
        voteFeePerKb(firstFeePerKbVote, 1);
        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(firstFeePerKbVote);

        /*
         *  2nd voting, authorizers 2 and 3 vote the same value
         */
        Coin secondFeePerKbVote = Coin.valueOf(100_000L);

        // First vote for a new value
        voteFeePerKb(secondFeePerKbVote, 1);
        // Get fee per kb, should return the first value voted because the new value has only 1 vote
        assertFeePerKbValue(firstFeePerKbVote);

        // Second vote for the same value, different authorizer
        voteFeePerKb(secondFeePerKbVote, 2);
        // Get fee per kb, should return the second round vote value
        assertFeePerKbValue(secondFeePerKbVote);

        /*
         *  3rd voting, authorizers 1 and 2 vote different values.
         *  Then authorizer 3 votes the same value as authorizer 1
         */
        Coin thirdFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(thirdFeePerKbVote, 0);
        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(secondFeePerKbVote);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, 1);
        // Get fee per kb, should return the second round voting value because the third value has only 2 different votes
        assertFeePerKbValue(secondFeePerKbVote);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(thirdFeePerKbVote, 2);
        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(thirdFeePerKbVote);

        /*
         *  4th voting, authorizers 1 and 2 vote the same value.
         *  Then authorizer 3 votes a different value
         */
        Coin fourthFeePerKbVote = Coin.valueOf(70_000L);

        // First vote for a new value
        voteFeePerKb(fourthFeePerKbVote, 0);
        // Get fee per kb, should return the third round voted value because the fourth round has only 1 vote
        assertFeePerKbValue(thirdFeePerKbVote);

        // Second vote for a same value, different authorizer
        voteFeePerKb(fourthFeePerKbVote, 1);
        // Get fee per kb, should have changed to fourthFeePerKb vote
        assertFeePerKbValue(fourthFeePerKbVote);

        // Third vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, 2);
        // Get fee per kb, should return fourthFeePerKbVote since the third vote is considered a new round.
        assertFeePerKbValue(fourthFeePerKbVote);

        /*
         *  5th voting, authorizers 1 and 3 vote the same value.
         *  The last vote of the previous voting is taken as a first vote (different vote), however,
         *  it changes its vote in this round for the same vote of the authorizer 1.
         */
        Coin fifthFeePerKbVote = Coin.valueOf(80_000L);

        // Send 1 vote from an authorizer
        voteFeePerKb(fifthFeePerKbVote, 0);
        // Get fee per kb, shouldn't have changed because the two first votes are different
        assertFeePerKbValue(fourthFeePerKbVote);

        // Second vote from a different authorizer
        voteFeePerKb(fifthFeePerKbVote, 2);
        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(fifthFeePerKbVote);

        /*
         *  6th voting, authorizers 1 vote for a different value.
         *  Authorizer 2 and 3 vote the same value.
         */
        Coin sixthFeePerKbVote = Coin.valueOf(30_000L);

        // First vote for a new value
        voteFeePerKb(differentFeePerKbVote, 0);
        // Get fee per kb, should return the fifthFeePerKbVote voted value because the voting has only 1 vote
        assertFeePerKbValue(fifthFeePerKbVote);

        // Second vote for a different value, different authorizer
        voteFeePerKb(sixthFeePerKbVote, 1);
        // Get fee per kb, should return the second round voting value because the third value has only 2 different votes
        assertFeePerKbValue(fifthFeePerKbVote);

        // Third vote from authorizer 3, same value as the second one voted
        voteFeePerKb(sixthFeePerKbVote, 2);
        // Get fee per kb, should return sixthFeePerKbVote value
        assertFeePerKbValue(sixthFeePerKbVote);

        /*
         *  7th voting, authorizers 1 and 3 vote same values.
         *  In between votes an unauthorized caller emits a vote similar to previous, but the fee per KB
         *  shouldn't change with this vote.
         */
        Coin seventhFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(seventhFeePerKbVote, 0);
        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(sixthFeePerKbVote);

        // Second vote for an unauthorized caller
        voteFeePerKbByUnauthorizedCaller(seventhFeePerKbVote);
        //It still has the previous voting round value
        assertFeePerKbValue(sixthFeePerKbVote);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(seventhFeePerKbVote, 2);
        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(seventhFeePerKbVote);

        /*
         *  8th voting, authorizers 1 and 3 vote same values.
         *  In between vote authorizer 2 an unauthorized caller the same value, but the fee per KB
         *  shouldn't change with an unauthorized caller vote.
         */
        Coin eighthFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(eighthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(seventhFeePerKbVote);

        // Second vote for a new different value
        voteFeePerKb(differentFeePerKbVote, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(seventhFeePerKbVote);

        // Third vote is same than the second vote value, but it is an unauthorized caller
        voteFeePerKbByUnauthorizedCaller(differentFeePerKbVote);
        //It still has the previous voting round value, since it shouldn't change with an unauthorized caller vote
        assertFeePerKbValue(seventhFeePerKbVote);

        // Fourth vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(eighthFeePerKbVote, 2);
        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(eighthFeePerKbVote);

        /*
         *  9th voting:
         *  An unauthorized caller and authorizer 2 vote same value.
         *  Authorizer 1 and 3 vote same values.
         *  It should change with the last vote from authorizer 3
         */
        Coin ninthFeePerKbVote = Coin.valueOf(90_000L);

        // First vote is by an unauthorized caller
        voteFeePerKbByUnauthorizedCaller(differentFeePerKbVote);
        //It still has the previous voting round value
        assertFeePerKbValue(eighthFeePerKbVote);

        // Second vote is by authorizer 1
        voteFeePerKb(ninthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(eighthFeePerKbVote);

        // Third vote is by authorizer 2. it votes similar then the unauthorized caller
        voteFeePerKb(differentFeePerKbVote, 1);
        //It still has the previous voting round value, since it shouldn't change with an unauthorized caller vote
        assertFeePerKbValue(eighthFeePerKbVote);

        // Fourth vote is by authorizer 3, same value than authorizer 1
        voteFeePerKb(ninthFeePerKbVote, 2);
        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(ninthFeePerKbVote);

        /*
         *  10th voting:
         *  Authorizers 1 and 2 vote same values. Authorizer 2 votes the first time a negative value
         *  Authorizer 2 and 3 vote for a negative value, but fee per KB not change since negative values
         *  are not permitted.
         *
         */
        Coin tenthFeePerKbVote = Coin.valueOf(20_000L);

        // First vote for a new value
        voteFeePerKb(tenthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(ninthFeePerKbVote);

        // Second vote, authorizer 2 votes a negative value
        voteFeePerKb(Coin.NEGATIVE_SATOSHI, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(ninthFeePerKbVote);

        // Third vote, authorizer 3, votes a negative value
        voteFeePerKb(Coin.NEGATIVE_SATOSHI, 2);
        //It still has the previous voting round value
        assertFeePerKbValue(ninthFeePerKbVote);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(tenthFeePerKbVote, 1);
        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(tenthFeePerKbVote);

        /*
         *  11th voting: excessive value case. An excessive value is a value greater than the max
         *  fee per KB value permitted
         *  Authorizers 1 and 2 vote same values. Authorizer 2 votes the first time an excessive value
         *  Authorizer 2 and 3 vote for an excessive value, but fee per KB not change since excessive values
         *  are not permitted.
         */
        Coin eleventhFeePerKbVote = Coin.valueOf(20_000L);

        // First vote for a new value
        voteFeePerKb(eleventhFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(tenthFeePerKbVote);

        // Second vote, authorizer 2 votes an excessive value
        voteFeePerKb(excessiveFeePerKbVote, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(tenthFeePerKbVote);

        // Third vote, authorizer 3, votes an excessive value
        voteFeePerKb(excessiveFeePerKbVote, 2);
        //It still has the previous voting round value
        assertFeePerKbValue(tenthFeePerKbVote);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(eleventhFeePerKbVote, 1);
        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(eleventhFeePerKbVote);

        /*
         *  12th voting: edge case where max fee per KB is voted by majority
         *  Authorizers 1 votes the max fee per KB value.
         *  Authorizer 2 votes  different value
         *  Authorizer 3 votes the max fee per KB value.
         */
        Coin twelfthFeePerKbVote = feePerKbConstants.getMaxFeePerKb();

        // First vote for a new value
        voteFeePerKb(twelfthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(eleventhFeePerKbVote);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(eleventhFeePerKbVote);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(twelfthFeePerKbVote, 2);
        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(twelfthFeePerKbVote);

        /*
         *  13th voting: edge case where a very low fee per KB is voted by majority
         *  Authorizers 1 votes a very low fee per KB value.
         *  Authorizer 2 votes  different value
         *  Authorizer 3 votes a very low fee per KB value.
         */
        Coin thirteenthFeePerKbVote = Coin.SATOSHI;

        // First vote for a new value
        voteFeePerKb(thirteenthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(twelfthFeePerKbVote);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(twelfthFeePerKbVote);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(thirteenthFeePerKbVote, 2);
        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(thirteenthFeePerKbVote);

        /*
         *  14th voting:
         *  Authorizers 1 and 2 vote same values. Authorizer 2 votes the first time a zero value
         *  Authorizer 2 and 3 vote for a zero value, but fee per KB not change since zero values
         *  are not permitted.
         *
         */
        Coin fourteenthFeePerKbVote = Coin.valueOf(10_000L);

        // First vote for a new value
        voteFeePerKb(fourteenthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(thirteenthFeePerKbVote);

        // Second vote, authorizer 2 votes a zero value
        voteFeePerKb(Coin.ZERO, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(thirteenthFeePerKbVote);

        // Third vote, authorizer 3, votes a zero value
        voteFeePerKb(Coin.ZERO, 2);
        //It still has the previous voting round value
        assertFeePerKbValue(thirteenthFeePerKbVote);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(fourteenthFeePerKbVote, 1);
        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(fourteenthFeePerKbVote);

        /*
         *  15th voting: authorized caller vote several times
         *  Authorizers 1 votes a very low fee per KB value.
         *  Authorizer 2 votes  different value
         *  Authorizer 3 votes a very low fee per KB value.
         */
        Coin fifteenthFeePerKbVote = Coin.valueOf(130_000L);

        Coin firstVoteFeePerKb  = Coin.valueOf(121_000L);
        // First vote for a new value
        voteFeePerKb(firstVoteFeePerKb, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        Coin secondVoteFeePerKb  = Coin.valueOf(122_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(secondVoteFeePerKb, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        Coin thirdVoteFeePerKb  = Coin.valueOf(123_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(thirdVoteFeePerKb, 2);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        // Again authorized callers vote

        Coin fourthVoteFeePerKb  = Coin.valueOf(124_000L);
        // First vote for a new value
        voteFeePerKb(fourthVoteFeePerKb, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        Coin fifthVoteFeePerKb  = Coin.valueOf(125_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(fifthVoteFeePerKb, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        Coin sixthVoteFeePerKb  = Coin.valueOf(126_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(sixthVoteFeePerKb, 2);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        // Again authorized callers vote

        Coin seventhVoteFeePerKb  = Coin.valueOf(127_000L);
        // First vote for a new value
        voteFeePerKb(seventhVoteFeePerKb, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        Coin eighthVoteFeePerKb  = Coin.valueOf(128_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(eighthVoteFeePerKb, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        Coin ninthVoteFeePerKb  = Coin.valueOf(129_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(ninthVoteFeePerKb, 2);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        // Again authorized callers vote

        // First vote for a new value
        voteFeePerKb(fifteenthFeePerKbVote, 0);
        //It still has the previous voting round value
        assertFeePerKbValue(fourteenthFeePerKbVote);

        // First vote for a new value
        voteFeePerKb(fifteenthFeePerKbVote, 1);
        //It still has the previous voting round value
        assertFeePerKbValue(fifteenthFeePerKbVote);
    }

    private void assertFeePerKbValue(Coin feePerKbExpectedResult) {
        Coin feePerKbActualResult = feePerKbSupport.getFeePerKb();
        assertEquals(feePerKbExpectedResult, feePerKbActualResult);
    }

    private void voteFeePerKb(Coin feePerKb, int authorizerIndex) {
        RskAddress authorizerAddress = this.getAuthorizedRskAddresses().get(authorizerIndex);
        Transaction voteTx = getTransactionFromCaller(authorizerAddress);
        feePerKbSupport.voteFeePerKbChange(voteTx, feePerKb, signatureCache);
    }

    private void voteFeePerKbByUnauthorizedCaller(Coin feePerKb) {
        RskAddress authorizerAddress = this.getUnauthorizedRskAddress();
        Transaction voteTx = getTransactionFromCaller(authorizerAddress);
        feePerKbSupport.voteFeePerKbChange(voteTx, feePerKb, signatureCache);
    }

    private Transaction getTransactionFromCaller(RskAddress authorizer) {
        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(authorizer);

        return txFromAuthorizedCaller;
    }

    private List<RskAddress> getAuthorizedRskAddresses() {
        return Stream.of(
            "a02db0ed94a5894bc6f9079bb9a2d93ada1917f3",
            "180a7edda4e640ea5a3e495e17a1efad260c39e9",
            "8418edc8fea47183116b4c8cd6a12e51a7e169c1"
        ).map(RskAddress::new).collect(Collectors.toList());
    }

    private RskAddress getUnauthorizedRskAddress() {
        return new RskAddress("e2a5070b4e2cb77fe22dff05d9dcdc4d3eaa6ead");
    }
}
