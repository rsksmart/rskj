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
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Objects;
import java.util.Optional;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
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
    FeePerKbConstants feePerKbConstants;
    @InjectMocks
    FeePerKbSupportImpl feePerKbSupport;


    @Test
    void getFeePerKb() {
        Coin mockCoin = mock(Coin.class);
        Optional<Coin> currentFeePerKb = Optional.of(mockCoin);
        when(provider.getFeePerKb()).thenReturn(currentFeePerKb);

        assertEquals(feePerKbSupport.getFeePerKb(), currentFeePerKb.get());
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);

        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(false);

        assertEquals(Integer.valueOf(FeePerKbResponseCode.UNAUTHORIZED_CALLER.getCode()),
            feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache));
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin feePerKb = mock(Coin.class);
        when(feePerKb.isPositive()).thenReturn(false);

        assertEquals(Integer.valueOf(FeePerKbResponseCode.NEGATIVE_FEE_VOTED.getCode()),
            feePerKbSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI, signatureCache));
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        when(authorizer.isAuthorized(tx, signatureCache)).thenReturn(true);

        Coin feePerKb = mock(Coin.class);
        when(feePerKb.isPositive()).thenReturn(true);

        Coin maxFeePerKb = mock(Coin.class);
        when(feePerKbConstants.getMaxFeePerKb()).thenReturn(maxFeePerKb);
        when(feePerKb.isGreaterThan(maxFeePerKb)).thenReturn(true);

        assertEquals(Integer.valueOf(FeePerKbResponseCode.EXCESSIVE_FEE_VOTED.getCode()),
            feePerKbSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB), signatureCache));
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
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

        assertEquals(Integer.valueOf(FeePerKbResponseCode.UNSUCCESSFUL_VOTE.getCode()),
            feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache));
    }

    @Test
    void voteFeePerKbChange_successfulVote() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
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

        assertEquals(Integer.valueOf(FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode()),
            feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache));
    }

    @Test
    void voteFeePerKbChange_genericError() {
        final String SET_FEE_PER_KB_ABI_FUNCTION = "setFeePerKb";
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(feePerKbConstants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
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

        assertEquals(Integer.valueOf(FeePerKbResponseCode.GENERIC_ERROR.getCode()),
            feePerKbSupport.voteFeePerKbChange(tx, Coin.CENT, signatureCache));

    }

    @Test
    void voteFeePerKbChange_nullFeeThrows() {
        Transaction tx = mock(Transaction.class);
        SignatureCache signatureCache = spy(
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

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
