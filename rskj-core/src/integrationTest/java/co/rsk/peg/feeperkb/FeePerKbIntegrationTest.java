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
import co.rsk.peg.utils.Caller;
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
class FeePerKbIntegrationTest {
    private final FeePerKbConstants feePerKbConstants = FeePerKbMainNetConstants.getInstance();
    private FeePerKbSupport feePerKbSupport;
    private SignatureCache signatureCache;
    private Coin currentFeePerKb;
    private Coin excessiveFeePerKbVote;
    private Coin differentFeePerKbVote;

    @BeforeAll
    void setUp() {
        StorageAccessor inMemoryStorage = new InMemoryStorage();
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(inMemoryStorage);
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
        signatureCache = mock(SignatureCache.class);
        Coin maxFeePerKbVote = feePerKbConstants.getMaxFeePerKb();
        excessiveFeePerKbVote = maxFeePerKbVote.add(Coin.SATOSHI);
        differentFeePerKbVote = Coin.valueOf(60_000L);
    }

    /**
     * Without any previous voting.
     */
    @Test
    @Order(0)
    void genesisFeePerKb() {
        Coin genesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();
        currentFeePerKb = genesisFeePerKb;
        assertFeePerKbValue(genesisFeePerKb);
    }

    /**
     * Authorizer 1 and 2 vote the same value
     */
    @Test
    @Order(1)
    void firstVoting() {
        Coin firstFeePerKbVote = Coin.valueOf(50_000L);
        // Send 1 vote from an authorizer
        voteFeePerKb(firstFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, shouldn't have changed because only one vote was added
        assertFeePerKbValue(currentFeePerKb);

        // Second vote from a different authorizer
        voteFeePerKb(firstFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = firstFeePerKbVote;

        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(firstFeePerKbVote);
    }

    /**
     * Authorizers 2 and 3 vote the same value
     */
    @Test
    @Order(2)
    void secondVoting() {
        Coin secondFeePerKbVote = Coin.valueOf(100_000L);
        // First vote for a new value
        voteFeePerKb(secondFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the first value voted because the new value has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for the same value, different authorizer
        voteFeePerKb(secondFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = secondFeePerKbVote;

        // Get fee per kb, should return the second round vote value
        assertFeePerKbValue(secondFeePerKbVote);
    }

    /**
     * Authorizers 1 and 2 vote different values. Then authorizer 3 votes the same value as
     * authorizer 1
     */
    @Test
    @Order(3)
    void thirdVoting() {
        Coin thirdFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(thirdFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second round voting value because the third value has only 2 different votes
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(thirdFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = thirdFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(thirdFeePerKbVote);
    }

    /**
     * Authorizers 1 and 2 vote the same value. Then authorizer 3 votes a different value
     */
    @Test
    @Order(4)
    void fourthVoting() {
        Coin fourthFeePerKbVote = Coin.valueOf(70_000L);

        // First vote for a new value
        voteFeePerKb(fourthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the third round voted value because the fourth round has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a same value, different authorizer
        voteFeePerKb(fourthFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should have changed to fourthFeePerKb vote
        assertFeePerKbValue(fourthFeePerKbVote);

        // Third vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = fourthFeePerKbVote;

        // Get fee per kb, should return fourthFeePerKbVote since the third vote is considered a new round.
        assertFeePerKbValue(fourthFeePerKbVote);
    }

    /**
     * Authorizers 1 and 3 vote the same value. The last vote of the previous voting is taken as a
     * first vote (different vote), however, it changes its vote in this round for the same vote of
     * the authorizer 1.
     */
    @Test
    @Order(5)
    void fifthVoting() {
        Coin fifthFeePerKbVote = Coin.valueOf(80_000L);

        // Send 1 vote from an authorizer
        voteFeePerKb(fifthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, shouldn't have changed because the two first votes are different
        assertFeePerKbValue(currentFeePerKb);

        // Second vote from a different authorizer
        voteFeePerKb(fifthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = fifthFeePerKbVote;

        // Get fee per kb, should return the newly voted value
        assertFeePerKbValue(fifthFeePerKbVote);
    }

    /**
     * Authorizers 1 vote for a different value. Authorizer 2 and 3 vote the same value.
     */
    @Test
    @Order(6)
    void sixthVoting() {
        Coin sixthFeePerKbVote = Coin.valueOf(30_000L);

        // First vote for a new value
        voteFeePerKb(differentFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the fifthFeePerKbVote voted value because the voting has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(sixthFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second round voting value because the third value has only 2 different votes
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the second one voted
        voteFeePerKb(sixthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = sixthFeePerKbVote;

        // Get fee per kb, should return sixthFeePerKbVote value
        assertFeePerKbValue(sixthFeePerKbVote);
    }

    /**
     * Authorizers 1 and 3 vote same values. In between votes an unauthorized caller emits a vote
     * similar to previous, but the fee per KB shouldn't change with this vote.
     */
    @Test
    @Order(7)
    void seventhVoting() {
        Coin seventhFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(seventhFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        // Get fee per kb, should return the second voted value because the third value has only 1 vote
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for an unauthorized caller
        voteFeePerKb(seventhFeePerKbVote, Caller.UNAUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(seventhFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = seventhFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(seventhFeePerKbVote);
    }

    /**
     * Authorizers 1 and 3 vote same values. In between vote authorizer 2 an unauthorized caller the
     * same value, but the fee per KB shouldn't change with an unauthorized caller vote.
     */
    @Test
    @Order(8)
    void eighthVoting() {
        Coin eighthFeePerKbVote = Coin.valueOf(40_000L);

        // First vote for a new value
        voteFeePerKb(eighthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a new different value
        voteFeePerKb(differentFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote is same than the second vote value, but it is an unauthorized caller
        voteFeePerKb(differentFeePerKbVote, Caller.UNAUTHORIZED.getRskAddress());
        //It still has the previous voting round value, since it shouldn't change with an unauthorized caller vote
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(eighthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = eighthFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(eighthFeePerKbVote);
    }

    /**
     * An unauthorized caller and authorizer 2 vote same value. Authorizer 1 and 3 vote same values.
     * It should change with the last vote from authorizer 3
     */
    @Test
    @Order(9)
    void ninthVoting() {
        Coin ninthFeePerKbVote = Coin.valueOf(90_000L);

        // First vote is by an unauthorized caller
        voteFeePerKb(differentFeePerKbVote, Caller.UNAUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote is by authorizer 1
        voteFeePerKb(ninthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote is by authorizer 2. it votes similar then the unauthorized caller
        voteFeePerKb(differentFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value, since it shouldn't change with an unauthorized caller vote
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote is by authorizer 3, same value than authorizer 1
        voteFeePerKb(ninthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = ninthFeePerKbVote;

        // Get fee per kb, should return the third vote value
        assertFeePerKbValue(ninthFeePerKbVote);
    }

    /**
     * Authorizers 1 and 2 vote same values. Authorizer 2 votes the first time a negative value
     * Authorizer 2 and 3 vote for a negative value, but fee per KB not change since negative values
     * are not permitted.
     */
    @Test
    @Order(10)
    void tenthVoting() {
        Coin tenthFeePerKbVote = Coin.valueOf(20_000L);

        // First vote for a new value
        voteFeePerKb(tenthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote, authorizer 2 votes a negative value
        voteFeePerKb(Coin.NEGATIVE_SATOSHI, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote, authorizer 3, votes a negative value
        voteFeePerKb(Coin.NEGATIVE_SATOSHI, Caller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(tenthFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = tenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(tenthFeePerKbVote);
    }

    /**
     * Excessive value case. An excessive value is a value greater than the max fee per KB value
     * permitted Authorizers 1 and 2 vote same values. Authorizer 2 votes the first time an
     * excessive value Authorizer 2 and 3 vote for an excessive value, but fee per KB not change
     * since excessive values are not permitted.
     */
    @Test
    @Order(11)
    void eleventhVoting() {
        Coin eleventhFeePerKbVote = Coin.valueOf(20_000L);

        // First vote for a new value
        voteFeePerKb(eleventhFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote, authorizer 2 votes an excessive value
        voteFeePerKb(excessiveFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote, authorizer 3, votes an excessive value
        voteFeePerKb(excessiveFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(eleventhFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = eleventhFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(eleventhFeePerKbVote);
    }

    /**
     * Edge case where max fee per KB is voted by majority Authorizers 1 votes the max fee per KB
     * value. Authorizer 2 votes  different value Authorizer 3 votes the max fee per KB value.
     */
    @Test
    @Order(12)
    void twelfthVoting() {
        Coin twelfthFeePerKbVote = feePerKbConstants.getMaxFeePerKb();

        // First vote for a new value
        voteFeePerKb(twelfthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(twelfthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = twelfthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(twelfthFeePerKbVote);
    }

    /**
     * Edge case where a very low fee per KB is voted by majority Authorizers 1 votes a very low fee
     * per KB value. Authorizer 2 votes  different value Authorizer 3 votes a very low fee per KB
     * value.
     */
    @Test
    @Order(13)
    void thirteenthVoting() {
        Coin thirteenthFeePerKbVote = Coin.SATOSHI;

        // First vote for a new value
        voteFeePerKb(thirteenthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(thirteenthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
        currentFeePerKb = thirteenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 3
        assertFeePerKbValue(thirteenthFeePerKbVote);
    }

    /**
     * Authorizers 1 and 2 vote same values. Authorizer 2 votes the first time a zero value
     * Authorizer 2 and 3 vote for a zero value, but fee per KB not change since zero values are not
     * permitted.
     */
    @Test
    @Order(14)
    void fourteenthVoting() {
        Coin fourteenthFeePerKbVote = Coin.valueOf(10_000L);

        // First vote for a new value
        voteFeePerKb(fourteenthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote, authorizer 2 votes a zero value
        voteFeePerKb(Coin.ZERO, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote, authorizer 3, votes a zero value
        voteFeePerKb(Coin.ZERO, Caller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Fourth vote, authorizer 2 votes again, this time for the same value that authorizer 1
        voteFeePerKb(fourteenthFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = fourteenthFeePerKbVote;

        // Get fee per kb, should return the value voted by authorizer 1 and 2 (second vote)
        assertFeePerKbValue(fourteenthFeePerKbVote);
    }

    /**
     * Authorized callers vote several times
     */
    @Test
    @Order(15)
    void fifteenthVoting() {
        Coin fifteenthFeePerKbVote = Coin.valueOf(130_000L);

        Coin firstVoteFeePerKb = Coin.valueOf(121_000L);
        // First vote for a new value
        voteFeePerKb(firstVoteFeePerKb, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin secondVoteFeePerKb = Coin.valueOf(122_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(secondVoteFeePerKb, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin thirdVoteFeePerKb = Coin.valueOf(123_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(thirdVoteFeePerKb, Caller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Again authorized callers vote

        Coin fourthVoteFeePerKb = Coin.valueOf(124_000L);
        // First vote for a new value
        voteFeePerKb(fourthVoteFeePerKb, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin fifthVoteFeePerKb = Coin.valueOf(125_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(fifthVoteFeePerKb, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin sixthVoteFeePerKb = Coin.valueOf(126_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(sixthVoteFeePerKb, Caller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Again authorized callers vote

        Coin seventhVoteFeePerKb = Coin.valueOf(127_000L);
        // First vote for a new value
        voteFeePerKb(seventhVoteFeePerKb, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin eighthVoteFeePerKb = Coin.valueOf(128_000L);
        // Second vote for a different value, different authorizer
        voteFeePerKb(eighthVoteFeePerKb, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        Coin ninthVoteFeePerKb = Coin.valueOf(129_000L);
        // Third vote for a different value, different authorizer
        voteFeePerKb(ninthVoteFeePerKb, Caller.THIRD_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Again authorized callers vote

        // First vote for a new value
        voteFeePerKb(fifteenthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a same value than the previous one
        voteFeePerKb(fifteenthFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        currentFeePerKb = fifteenthFeePerKbVote;

        //It still has the previous voting round value
        assertFeePerKbValue(fifteenthFeePerKbVote);
    }

    /**
     * Authorized callers vote same current fee per KB value Authorizers 1 votes for the same
     * current fee per KB value. Authorizer 2 votes  different value Authorizer 3 votes for the same
     * current fee per KB value.
     */
    @Test
    @Order(16)
    void sixteenthVoting() {
        Coin sixteenthFeePerKbVote = feePerKbSupport.getFeePerKb();

        // First vote for a new value
        voteFeePerKb(sixteenthFeePerKbVote, Caller.FIRST_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Second vote for a different value, different authorizer
        voteFeePerKb(differentFeePerKbVote, Caller.SECOND_AUTHORIZED.getRskAddress());
        //It still has the previous voting round value
        assertFeePerKbValue(currentFeePerKb);

        // Third vote from authorizer 3, same value as the first one voted from authorizer 1
        voteFeePerKb(sixteenthFeePerKbVote, Caller.THIRD_AUTHORIZED.getRskAddress());
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
