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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeePerKbSupportImplTest {

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
    void voteFeePerKbChange_unsuccessfulVote_shouldReturnUnsuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        ABICallElection feePerKbElection = mock(ABICallElection.class);
        when(feePerKbElection.vote(any(), any())).thenReturn(false);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        Coin feePerKbVote = Coin.valueOf(50_000L);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.UNSUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_successfulFeePerKbVote_shouldReturnSuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        ABICallElection feePerKbElection = mock(ABICallElection.class);
        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        when(feePerKbElection.vote(any(), any())).thenReturn(true);

        Coin feePerKbVote = Coin.valueOf(50_000L);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_successfulFeePerKbChanged_shouldReturnSuccessfulFeeVotedResponseCode() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        final Coin feePerKbVote = Coin.valueOf(50_000L);

        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        ABICallElection feePerKbElection = mock(ABICallElection.class);
        when(feePerKbElection.vote(any(), any())).thenReturn(true);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKbVote)});
        when(feePerKbElection.getWinner()).thenReturn(Optional.of(feeVote));

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
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
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin feePerKb = Coin.valueOf(50_000L);
        Map<ABICallSpec, List<RskAddress>> votes = new HashMap<>();
        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        List<RskAddress> rskAddress = new ArrayList<>();
        rskAddress.add(this.getAuthorizedRskAddress());
        votes.put(feeVote,rskAddress);

        ABICallElection feePerKbElection = new ABICallElection(authorizer, votes);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.UNSUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_repeatedVote_sameRskAddressDifferentFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin firstFeePerKb = Coin.valueOf(50_000L);
        Map<ABICallSpec, List<RskAddress>> votes = new HashMap<>();
        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{BridgeSerializationUtils.serializeCoin(firstFeePerKb)});
        List<RskAddress> rskAddress = new ArrayList<>();
        rskAddress.add(this.getAuthorizedRskAddress());
        votes.put(feeVote,rskAddress);

        ABICallElection feePerKbElection = new ABICallElection(authorizer, votes);
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        Coin secondFeePerKb = feePerKbConstants.getMaxFeePerKb();

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, secondFeePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_winnerFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        Coin feePerKb = Coin.valueOf(50_000L);
        Map<ABICallSpec, List<RskAddress>> votes = new HashMap<>();
        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        List<RskAddress> rskAddress = new ArrayList<>();
        rskAddress.add(this.getSecondAuthorizedRskAddress());
        votes.put(feeVote,rskAddress);

        ABICallElection feePerKbElection = new ABICallElection(authorizer, votes);
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
        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(this.getAuthorizedRskAddress());

        return txFromAuthorizedCaller;
    }

    private RskAddress getUnauthorizedRskAddress(){
        return new RskAddress("e2a5070b4e2cb77fe22dff05d9dcdc4d3eaa6ead");
    }

    private RskAddress getAuthorizedRskAddress(){
        return new RskAddress("a02db0ed94a5894bc6f9079bb9a2d93ada1917f3");
    }

    private RskAddress getSecondAuthorizedRskAddress(){
        return new RskAddress("180a7edda4e640ea5a3e495e17a1efad260c39e9");
    }
}
