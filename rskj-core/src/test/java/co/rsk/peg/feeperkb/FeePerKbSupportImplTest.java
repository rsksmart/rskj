package co.rsk.peg.feeperkb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Objects;
import java.util.Optional;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeePerKbSupportImplTest {

    @Mock
    FeePerKbStorageProvider provider;
    @Mock
    FeePerKbMainNetConstants feePerKbConstants;
    @InjectMocks
    FeePerKbSupportImpl feePerKbSupport;


    @Test
    void getFeePerKb() {
        Optional<Coin> currentFeePerKb = Optional.of(Coin.COIN);
        when(provider.getFeePerKb()).thenReturn(currentFeePerKb);

        Coin actualResult = feePerKbSupport.getFeePerKb();

        Coin expectedResult = currentFeePerKb.get();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void getFeePerKb_nullInStorageProvider() {
        Optional<Coin> genesisFeePerKb = Optional.of(Coin.MILLICOIN.multiply(5));
        when(provider.getFeePerKb()).thenReturn(genesisFeePerKb);

        Coin actualResult = feePerKbSupport.getFeePerKb();

        Coin expectedResult = genesisFeePerKb.get();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);

        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(false);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.UNAUTHORIZED_CALLER.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin negativeFeePerKb = mock(Coin.class);
        when(negativeFeePerKb.isPositive()).thenReturn(false);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.NEGATIVE_FEE_VOTED.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin feePerKb = mock(Coin.class);
        when(feePerKb.isPositive()).thenReturn(true);

        Coin maxFeePerKb = mock(Coin.class);
        when(feePerKbConstants.getMaxFeePerKb()).thenReturn(maxFeePerKb);
        when(feePerKb.isGreaterThan(maxFeePerKb)).thenReturn(true);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB), signatureCache);

        Integer expectedResult = FeePerKbResponseCode.EXCESSIVE_FEE_VOTED.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin feePerKb = mock(Coin.class);
        when(feePerKb.isPositive()).thenReturn(true);

        Coin maxFeePerKb = spy(Coin.CENT);
        when(feePerKbConstants.getMaxFeePerKb()).thenReturn(maxFeePerKb);
        when(feePerKb.isGreaterThan(maxFeePerKb)).thenReturn(false);

        ABICallElection feePerKbElection = spy(new ABICallElection(authorizer));
        when(provider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);

        ABICallSpec feeVote = spy(new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeCoin(feePerKb)}));
        when(feePerKbElection.vote(feeVote, tx.getSender(signatureCache))).thenReturn(false);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.UNSUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_successfulVote() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin feePerKb = mock(Coin.class);
        when(feePerKb.isPositive()).thenReturn(true);

        Coin maxFeePerKb = spy(Coin.CENT);
        when(feePerKbConstants.getMaxFeePerKb()).thenReturn(maxFeePerKb);
        when(feePerKb.isGreaterThan(maxFeePerKb)).thenReturn(false);

        ABICallElection feePerKbElection = spy(new ABICallElection(authorizer));
        when(provider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        ABICallSpec feeVote = spy(new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeCoin(feePerKb)}));
        when(feePerKbElection.vote(feeVote, tx.getSender(signatureCache))).thenReturn(true);

        ABICallSpec abiCallSpec = spy(new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeCoin(feePerKb)}));
        Optional<ABICallSpec> winnerOptional = Optional.of(abiCallSpec);
        when(feePerKbElection.getWinner()).thenReturn(winnerOptional);

        ABICallSpec winner = winnerOptional.get();
        Coin winnerFee = spy(Objects.requireNonNull(
            BridgeSerializationUtils.deserializeCoin(winner.getArguments()[0])));

        provider.setFeePerKb(winnerFee);
        feePerKbElection.clear();

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_genericError() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin feePerKb = mock(Coin.class);
        when(feePerKb.isPositive()).thenReturn(true);

        Coin maxFeePerKb = spy(Coin.CENT);
        when(feePerKbConstants.getMaxFeePerKb()).thenReturn(maxFeePerKb);
        when(feePerKb.isGreaterThan(maxFeePerKb)).thenReturn(false);

        ABICallElection feePerKbElection = spy(new ABICallElection(authorizer));
        when(provider.getFeePerKbElection(authorizer)).thenReturn(feePerKbElection);
        ABICallSpec feeVote = spy(new ABICallSpec(SET_FEE_PER_KB_ABI_FUNCTION, new byte[][]{
            BridgeSerializationUtils.serializeCoin(feePerKb)}));
        when(feePerKbElection.vote(feeVote, tx.getSender(signatureCache))).thenReturn(true);

        ABICallSpec abiCallSpec = mock(ABICallSpec.class);
        Optional<ABICallSpec> winnerOptional = Optional.of(abiCallSpec);
        when(feePerKbElection.getWinner()).thenReturn(winnerOptional);

        Integer actualResult = feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.GENERIC_ERROR.getCode();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void voteFeePerKbChange_nullFeeThrows() {
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = mock(SignatureCache.class);

        assertThrows(NullPointerException.class,
            () -> feePerKbSupport.voteFeePerKbChange(tx, null, signatureCache));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    void save() {
        doNothing().when(provider).save();

        feePerKbSupport.save();

        verify(provider, times(1)).save();
    }
}
