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
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
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
    void voteFeePerKbChange_excessiveFeePerKbValue_shouldReturnExcessiveFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.COIN, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.EXCESSIVE_FEE_VOTED.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_shouldReturnUnsuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        ABICallElection feePerKbElection = mock(ABICallElection.class);
        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.valueOf(50_000L), signatureCache);

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

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.valueOf(50_000L), signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_successfulFeePerKbChanged_shouldReturnSuccessfulFeeVotedResponseCode() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        final Coin feePerKb = Coin.valueOf(50_000L);
        SignatureCache signatureCache = mock(SignatureCache.class);
        Transaction tx = getTransactionFromAuthorizedCaller(signatureCache);
        ABICallElection feePerKbElection = mock(ABICallElection.class);
        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        when(storageProvider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        when(feePerKbElection.vote(any(), any())).thenReturn(true);
        ABICallSpec feeVote = new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        when(feePerKbElection.getWinner()).thenReturn(Optional.of(feeVote));

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, feePerKb, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_nullFeeThrows() {
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);

        assertThrows(NullPointerException.class,
            () -> feePerKbSupport.voteFeePerKbChange(tx, null, signatureCache));
        verify(storageProvider, never()).setFeePerKb(any());
    }

    @Test
    void save() {
        doNothing().when(storageProvider).save();

        feePerKbSupport.save();

        verify(storageProvider, times(1)).save();
    }

    private Transaction getTransactionFromUnauthorizedCaller(SignatureCache signatureCache) {
        ECKey unauthorizedKey = ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2"));
        RskAddress unauthorizedCallerAddress = new RskAddress(unauthorizedKey.getAddress());

        Transaction txFromUnauthorizedCaller = mock(Transaction.class);
        when(txFromUnauthorizedCaller.getSender(signatureCache)).thenReturn(unauthorizedCallerAddress);

        return txFromUnauthorizedCaller;
    }

    private Transaction getTransactionFromAuthorizedCaller(SignatureCache signatureCache) {
        RskAddress authorizedCallerAddress = new RskAddress("a02db0ed94a5894bc6f9079bb9a2d93ada1917f3");

        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(authorizedCallerAddress);

        return txFromAuthorizedCaller;
    }
}
