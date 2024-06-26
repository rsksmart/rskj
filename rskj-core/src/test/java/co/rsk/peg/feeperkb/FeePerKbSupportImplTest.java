package co.rsk.peg.feeperkb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeePerKbSupportImplTest {

    private static final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";

    private FeePerKbStorageProvider storageProvider;
    private FeePerKbConstants feePerKbConstants;
    private FeePerKbSupportImpl feePerKbSupport;

    @BeforeEach
    void setUp() {
        storageProvider = mock(FeePerKbStorageProvider.class);
        feePerKbConstants = FeePerKbMainNetConstants.getInstance();
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, storageProvider);
    }

    @Test
    void getFeePerKb() {
        Optional<Coin> currentFeePerKb = Optional.of(Coin.valueOf(50_000L));
        when(storageProvider.getFeePerKb()).thenReturn(currentFeePerKb);

        Coin actualResult = feePerKbSupport.getFeePerKb();

        Coin expectedResult = currentFeePerKb.get();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void getFeePerKb_nullInStorageProvider_shouldReturnGenesisFeePerKb() {
        Coin actualResult = feePerKbSupport.getFeePerKb();

        Coin expectedResult = feePerKbConstants.getGenesisFeePerKb();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_withUnauthorizedSignature_shouldReturnUnauthorizedCallerResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromUnauthorizedCaller(signatureCache);
        Coin feePerKbVote = Coin.valueOf(50_000L);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.UNAUTHORIZED_CALLER.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_negativeFeePerKbValue_shouldReturnNegativeFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        Coin negativeFeePerKbVote = Coin.NEGATIVE_SATOSHI;

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, negativeFeePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.NEGATIVE_FEE_VOTED.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_aboveMaxFeePerKbValue_shouldReturnExcessiveFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        Coin maxFeePerKb = feePerKbConstants.getMaxFeePerKb();
        Coin excessiveFeePerKbVote = maxFeePerKb.add(Coin.SATOSHI);

        Integer aboveMaxValueVotedResult = feePerKbSupport.voteFeePerKbChange(tx, excessiveFeePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.EXCESSIVE_FEE_VOTED.getCode();
        assertEquals(expectedResult, aboveMaxValueVotedResult);
    }

    @Test
    void voteFeePerKbChange_zeroFeePerKbValue_shouldReturnNegativeFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        Coin zeroFeePerKb = Coin.ZERO;

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, zeroFeePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.NEGATIVE_FEE_VOTED.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_veryLowFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        ABICallElection feePerKbElection = new ABICallElection(authorizer);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        Coin veryLowFeePerKb = Coin.SATOSHI;

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, veryLowFeePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_equalMaxFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        ABICallElection feePerKbElection = new ABICallElection(authorizer);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        Coin maxFeePerKb = feePerKbConstants.getMaxFeePerKb();

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, maxFeePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_repeatedVote_sameRskAddressSameFeePerKbValue_shouldReturnUnsuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin feePerKb = Coin.valueOf(50_000L);
        RskAddress previousVoter = this.getAuthorizedRskAddresses().get(0);
        ABICallElection feePerKbElection = getAbiCallElectionWithExistingVote(authorizer, feePerKb, previousVoter);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.UNSUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_repeatedVote_sameRskAddressDifferentFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin firstFeePerKb = Coin.valueOf(50_000L);
        RskAddress previousVoter = this.getAuthorizedRskAddresses().get(0);
        ABICallElection feePerKbElection = getAbiCallElectionWithExistingVote(authorizer, firstFeePerKb, previousVoter);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        Coin secondFeePerKb = feePerKbConstants.getMaxFeePerKb();

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, secondFeePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_winnerFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin feePerKb = Coin.valueOf(50_000L);

        RskAddress previousVoter = this.getAuthorizedRskAddresses().get(1);
        ABICallElection feePerKbElection = getAbiCallElectionWithExistingVote(authorizer, feePerKb, previousVoter);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_nullFeeThrows() {
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);

        assertThrows(NullPointerException.class, () -> feePerKbSupport.voteFeePerKbChange(tx, null, signatureCache));
        verify(storageProvider, never()).setFeePerKb(any());
    }

    @Test
    void save() {
        doNothing().when(storageProvider).save();

        feePerKbSupport.save();

        verify(storageProvider, times(1)).save();
    }

    private Transaction getTransactionFromUnauthorizedCaller(SignatureCache signatureCache) {
        Transaction txFromUnauthorizedCaller = mock(Transaction.class);
        when(txFromUnauthorizedCaller.getSender(signatureCache)).thenReturn(this.getUnauthorizedRskAddress());

        return txFromUnauthorizedCaller;
    }

    private Transaction getTransactionFromAuthorizedCaller(SignatureCache signatureCache) {
        RskAddress authorizedRskAddress = this.getAuthorizedRskAddresses().get(0);
        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(authorizedRskAddress);

        return txFromAuthorizedCaller;
    }

    private RskAddress getUnauthorizedRskAddress(){
        return new RskAddress("e2a5070b4e2cb77fe22dff05d9dcdc4d3eaa6ead");
    }

    private List<RskAddress> getAuthorizedRskAddresses(){
        return Stream.of(
            "a02db0ed94a5894bc6f9079bb9a2d93ada1917f3",
            "180a7edda4e640ea5a3e495e17a1efad260c39e9",
            "8418edc8fea47183116b4c8cd6a12e51a7e169c1"
        ).map(RskAddress::new).collect(Collectors.toList());
    }

    private ABICallElection getAbiCallElectionWithExistingVote(
        AddressBasedAuthorizer authorizer,
        Coin feePerKbVote,
        RskAddress voter) {

        byte[] feePerKbVoteSerialized = BridgeSerializationUtils.serializeCoin(feePerKbVote);
        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{feePerKbVoteSerialized});

        List<RskAddress> voters = Collections.singletonList(voter);
        Map<ABICallSpec, List<RskAddress>> existingVotes = new HashMap<>();
        existingVotes.put(feeVote, voters);

        return new ABICallElection(authorizer, existingVotes);
    }
}
