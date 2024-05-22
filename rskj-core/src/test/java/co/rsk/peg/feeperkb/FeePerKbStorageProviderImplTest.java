package co.rsk.peg.feeperkb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeePerKbStorageProviderImplTest {

    @Mock
    StorageAccessor bridgeStorageAccessor;
    @InjectMocks
    FeePerKbStorageProviderImpl feePerKbStorageProvider;

    @Test
    void getFeePerKb() {
        Optional<Coin> feePerKb = Optional.of(Coin.SATOSHI);
        when(bridgeStorageAccessor.safeGetFromRepository(any(), any())).thenReturn(feePerKb.get());

        Optional<Coin> actualResult = feePerKbStorageProvider.getFeePerKb();

        assertEquals(feePerKb, actualResult);
    }

    @Test
    void getFeePerKb_whenFeePerKbIsNotNull() {
        Optional<Coin> feePerKb = Optional.of(Coin.SATOSHI);
        feePerKbStorageProvider.setFeePerKb(feePerKb.get());

        Optional<Coin> actualResult = feePerKbStorageProvider.getFeePerKb();

        assertEquals(feePerKb, actualResult);
    }

    @Test
    void getFeePerKbElection() {
        FeePerKbConstants feePerKbConstants = FeePerKbMainNetConstants.getInstance();
        AddressBasedAuthorizer authorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();
        ABICallElection abiCallElection = BridgeSerializationUtils.deserializeElection(new byte[0],
            feePerKbConstants.getFeePerKbChangeAuthorizer());
        when(bridgeStorageAccessor.safeGetFromRepository(any(), any())).thenReturn(abiCallElection);

        ABICallElection actualResult = feePerKbStorageProvider.getFeePerKbElection(authorizer);

        assertEquals(abiCallElection, actualResult);
    }

    @Test
    void save() {
        feePerKbStorageProvider.setFeePerKb(Coin.SATOSHI);
        doNothing().when(bridgeStorageAccessor).safeSaveToRepository(any(), any(), any());
        doNothing().when(bridgeStorageAccessor).safeSaveToRepository(any(), any(), any());

        feePerKbStorageProvider.save();

        verify(bridgeStorageAccessor, times(1)).safeSaveToRepository(any(), any(), any());
    }

    @Test
    void save_whenFeePerKbAndFeePerKbElectionAreNull() {
        doNothing().when(bridgeStorageAccessor).safeSaveToRepository(any(), any(), any());
        doNothing().when(bridgeStorageAccessor).safeSaveToRepository(any(), any(), any());

        feePerKbStorageProvider.save();

        verify(bridgeStorageAccessor, never()).safeSaveToRepository(any(), any(), any());
    }
}
