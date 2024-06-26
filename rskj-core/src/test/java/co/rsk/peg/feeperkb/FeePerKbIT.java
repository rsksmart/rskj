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
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class FeePerKbIT {
    private static final Coin differentFeePerKbVote = Coin.valueOf(60_000L);
    private final FeePerKbConstants feePerKbConstants = FeePerKbMainNetConstants.getInstance();
    private FeePerKbSupport feePerKbSupport;
    private SignatureCache signatureCache;
    private Coin currentFeePerKb; // it is used to guarantee the value from getFeePerKb() contains the value expected

    @BeforeAll
    void setUp() {
        StorageAccessor inMemoryStorage = new InMemoryStorage();
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(inMemoryStorage);
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
        signatureCache = mock(SignatureCache.class);
    }

    @Test
    @Order(0)
    void getGenesisFeePerKb_whenNotExistsPreviousVoting_shouldReturnGenesisFeePerKbValue() {
        Coin genesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();
        currentFeePerKb = genesisFeePerKb;
        assertFeePerKbValue(genesisFeePerKb);
    }

    @Test
    @Order(1)
    void voteFeePerKbChange_whenAuthorizerOneAndTwoVoteSameValue_shouldSetValueVoted() {
        Coin firstFeePerKbVote = Coin.valueOf(50_000L);
        // Send 1 vote from an authorizer
        voteFeePerKb(firstFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, shouldn't have changed because only one vote was added
        assertFeePerKbValue(currentFeePerKb);

        // Second vote from a different authorizer
        voteFeePerKb(firstFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = firstFeePerKbVote;

        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(firstFeePerKbVote);
    }

    @Test
    @Order(2)
    void voteFeePerKbChange_whenAuthorizerTwoAndThreeVoteSameValue_shouldSetValueVoted() {
        Coin secondFeePerKbVote = Coin.valueOf(100_000L);
        // First vote for a new value
        voteFeePerKb(secondFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the first value voted because the new value has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for the same value, different authorizer
        voteFeePerKb(secondFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = secondFeePerKbVote;

        // Get fee per kb, should return the second round vote value
        assertFeePerKbValue(secondFeePerKbVote);
    }

    @Test
    @Order(3)
    void voteFeePerKbChange_whenAuthorizerOneAndTwoVoteDifferentValueAndAuthorizerVoteSameAsAuthorizerOne_shouldSetValueVotedByOneAndThree() {
        Coin thirdFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(thirdFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second round voting value because the third value has only 2 different votes
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(thirdFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = thirdFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(thirdFeePerKbVote);
    }

    @Test
    @Order(4)
    void voteFeePerKbChange_whenAuthorizerOneAndTwoVoteSameValueAndAuthorizerThreeVotesDifferentValue_shouldSetValueVotedByOneAndTwo() {
        Coin fourthFeePerKbVote = Coin.valueOf(70_000L);

        // First vote for a new value
        voteFeePerKb(fourthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the third round voted value because the fourth round has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a same value, different authorizer
        voteFeePerKb(fourthFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should have changed to fourthFeePerKb vote
        assertFeePerKbValue(fourthFeePerKbVote);

        // Third vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = fourthFeePerKbVote;

        // Get fee per kb, should return fourthFeePerKbVote since the third vote is considered a new round.
        assertFeePerKbValue(fourthFeePerKbVote);
    }

    @Test
    @Order(5)
    void voteFeePerKbChange_whenAuthorizerOneAndThreeVoteSameValueThenLastVoteOfPreviousVotingTakenAsFirstDifferentVote_shouldSetValueVotedByOneAndThree() {
        Coin fifthFeePerKbVote = Coin.valueOf(80_000L);

        // Send 1 vote from an authorizer
        voteFeePerKb(fifthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, shouldn't have changed because the two first votes are different
        assertFeePerKbValue(currentFeePerKb);

        // Second vote from a different authorizer
        voteFeePerKb(fifthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = fifthFeePerKbVote;

        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(fifthFeePerKbVote);
    }

    @Test
    @Order(6)
    void voteFeePerKbChange_whenAuthorizerOneVotesDifferentValueAndAuthorizerTwoAndThreeVoteSameValue_shouldSetValueVotedByTwoAndThree() {
        Coin sixthFeePerKbVote = Coin.valueOf(30_000L);

        // First vote for a new value
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the fifthFeePerKbVote voted value because the voting has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(sixthFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second round voting value because the third value has only 2 different votes
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the second one voted
        voteFeePerKb(sixthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = sixthFeePerKbVote;

        // Get fee per kb, should return sixthFeePerKbVote value
        assertFeePerKbValue(sixthFeePerKbVote);
    }

    @Test
    @Order(7)
    void voteFeePerKbChange_whenAuthorizerOneAndThreeVoteSameValueAndInBetweenVotesUnauthorizedCallerSameValueThanOne_shouldSetValueWithLastVoteEmittedByThree() {
        Coin seventhFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(seventhFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for an unauthorized caller
        voteFeePerKb(seventhFeePerKbVote, FeePerKbVoteCaller.UNAUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(seventhFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = seventhFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(seventhFeePerKbVote);
    }

    @Test
    @Order(8)
    void voteFeePerKbChange_whenAuthorizerOneAndThreeVoteSameValueThenInBetweenAuthorizerTwoAndUnauthorizedCallerVoteSameValue_shouldSetValueVotedByOneAndThree() {
        Coin eighthFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(eighthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a new different value
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote is same than the second vote value, but it is an unauthorized caller
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.UNAUTHORIZED.getRskAddress());
        //It still has the previous voting round value, since it shouldn't change with an unauthorized caller vote
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(eighthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = eighthFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(eighthFeePerKbVote);
    }

    @Test
    @Order(9)
    void voteFeePerKbChange_whenUnauthorizedCallerAndAuthorizerTwoVoteSameValueThenNotChangeValueAndAuthorizerOneAndTwoVoteSameValue_shouldSetValueVotedByOneAndThree() {
        Coin ninthFeePerKbVote = Coin.valueOf(90_000L);

        // First vote is by an unauthorized caller
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.UNAUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote is by authorizer 1
        voteFeePerKb(ninthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote is by authorizer 2, who votes the same as the unauthorized caller
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value, since it shouldn't change with an unauthorized caller vote
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote is by authorizer 3, same value than authorizer 1
        voteFeePerKb(ninthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = ninthFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(ninthFeePerKbVote);
    }

    @Test
    @Order(10)
    void voteFeePerKbChange_whenAuthorizerOneAndTwoVoteSameValueAndFirstTimeAuthorizerTwoVotesNegativeValueAndAuthorizerThreeVotesSameNegativeValueThanAuthorizerTwo_shouldSetSamePositiveValueVotedByOneAndTwo() {
        Coin tenthFeePerKbVote = Coin.valueOf(20_000L);

        // First vote for a new value
        voteFeePerKb(tenthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote, authorizer 2 votes a negative value
        voteFeePerKb(Coin.NEGATIVE_SATOSHI, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote, authorizer 3, votes a negative value
        voteFeePerKb(Coin.NEGATIVE_SATOSHI, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(tenthFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = tenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(tenthFeePerKbVote);
    }

    @Test
    @Order(11)
    void voteFeePerKbChange_whenAuthorizerOneAndTwoVoteSameValueAndAuthorizerTwoVotesSameTimeExcessiveValueAndAuthorizerThreeVotesSameExcessiveValueAndSecondTimeAuthorizerTwoVotesSameThanOne_shouldSetValueVotedByOneAndTwo() {
        Coin eleventhFeePerKbVote = Coin.valueOf(20_000L);
        Coin maxFeePerKbVote = feePerKbConstants.getMaxFeePerKb();
        Coin excessiveFeePerKbVote = maxFeePerKbVote.add(Coin.SATOSHI);

        // First vote for a new value
        voteFeePerKb(eleventhFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote, authorizer 2 votes an excessive value
        voteFeePerKb(excessiveFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote, authorizer 3, votes an excessive value
        voteFeePerKb(excessiveFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(eleventhFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = eleventhFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(eleventhFeePerKbVote);
    }

    @Test
    @Order(12)
    void voteFeePerKbChange_whenAuthorizerOneAndThreeVoteSameMaxValuePermittedAndInBetweenAuthorizerTwoVoteDifferentValue_shouldSetValueVotedByOneAndThree() {
        Coin twelfthFeePerKbVote = feePerKbConstants.getMaxFeePerKb();

        // First vote for a new value
        voteFeePerKb(twelfthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(twelfthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = twelfthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(twelfthFeePerKbVote);
    }

    @Test
    @Order(13)
    void voteFeePerKbChange_whenAuthorizerOneAndThreeVoteSameVeryLowValueAndInBetweenAuthorizerTwoVotesDifferentValue_shouldSetValueVotedByOneAndThree() {
        Coin thirteenthFeePerKbVote = Coin.SATOSHI;

        // First vote for a new value
        voteFeePerKb(thirteenthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(thirteenthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = thirteenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(thirteenthFeePerKbVote);
    }

    @Test
    @Order(14)
    void voteFeePerKbChange_whenAuthorizerOneAndTwoVoteSameValidValueAndFirstTimeAuthorizerTwoVotesZeroAndAuthorizerThreeVotesSameZeroValueAndLastVoteAuthorizerTwoVotesSameThanOne_shouldSetValueVotedByOneAndTwo() {
        Coin fourteenthFeePerKbVote = Coin.valueOf(10_000L);

        // First vote for a new value
        voteFeePerKb(fourteenthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote, authorizer 2 votes a zero value
        voteFeePerKb(Coin.ZERO, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote, authorizer 3, votes a zero value
        voteFeePerKb(Coin.ZERO, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(fourteenthFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = fourteenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(fourteenthFeePerKbVote);
    }

    @Test
    @Order(15)
    void voteFeePerKbChange_whenSeveralAuthorizersVoteSeveralTimesDifferentValuesAndLastTwoVotesByAuthorizerOneAndTwoAreEqual_shouldSetValueVotedByOneAndTwoLastVotes() {
        Coin fifteenthFeePerKbVote = Coin.valueOf(130_000L);

        Coin firstVoteFeePerKb = Coin.valueOf(121_000L);
        // First vote for a new value
        voteFeePerKb(firstVoteFeePerKb, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin secondVoteFeePerKb = Coin.valueOf(122_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(secondVoteFeePerKb, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin thirdVoteFeePerKb = Coin.valueOf(123_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(thirdVoteFeePerKb, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Again authorized callers vote

        Coin fourthVoteFeePerKb = Coin.valueOf(124_000L);
        // First vote for a new value
        voteFeePerKb(fourthVoteFeePerKb, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin fifthVoteFeePerKb = Coin.valueOf(125_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(fifthVoteFeePerKb, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin sixthVoteFeePerKb = Coin.valueOf(126_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(sixthVoteFeePerKb, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Again authorized callers vote

        Coin seventhVoteFeePerKb = Coin.valueOf(127_000L);
        // First vote for a new value
        voteFeePerKb(seventhVoteFeePerKb, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin eighthVoteFeePerKb = Coin.valueOf(128_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(eighthVoteFeePerKb, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin ninthVoteFeePerKb = Coin.valueOf(129_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(ninthVoteFeePerKb, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Again authorized callers vote

        // First vote for a new value
        voteFeePerKb(fifteenthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a same value than the previous one
        voteFeePerKb(fifteenthFeePerKbVote, FeePerKbVoteCaller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = fifteenthFeePerKbVote;

        //It still has the previous voting round value
        assertFeePerKbValue(fifteenthFeePerKbVote);
    }

    @Test
    @Order(16)
    void voteFeePerKbChange_whenVotingSameCurrentFeePerKbValue_shouldSetValueVoted() {
        Coin sixteenthFeePerKbVote = feePerKbSupport.getFeePerKb();

        // First vote for a new value
        voteFeePerKb(sixteenthFeePerKbVote, FeePerKbVoteCaller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote from authorizer 3 is the same as first vote
        voteFeePerKb(sixteenthFeePerKbVote, FeePerKbVoteCaller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = sixteenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(sixteenthFeePerKbVote);
    }

    private void assertFeePerKbValue(Coin feePerKbExpectedResult) {
        Coin feePerKbActualResult = feePerKbSupport.getFeePerKb();
        assertEquals(feePerKbExpectedResult, feePerKbActualResult);
    }

    private void voteFeePerKb(Coin feePerKb, RskAddress caller) {
        Transaction voteTx = getTransactionFromCaller(caller);
        feePerKbSupport.voteFeePerKbChange(voteTx, feePerKb, signatureCache);
    }

    private Transaction getTransactionFromCaller(RskAddress authorizer) {
        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(authorizer);

        return txFromAuthorizedCaller;
    }
}
