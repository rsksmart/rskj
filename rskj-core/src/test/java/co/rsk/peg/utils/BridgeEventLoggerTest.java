package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class BridgeEventLoggerTest {

    private BridgeEventLogger eventLogger;
    private BtcTransaction btcTxMock;
    private Keccak256 rskTxHash;

    @BeforeEach
    public void setup() {
        eventLogger = spy(BridgeEventLogger.class);
        btcTxMock = mock(BtcTransaction.class);
        rskTxHash = PegTestUtils.createHash3(1);
    }

    @Test
    public void testLogLockBtc() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logLockBtc(mock(RskAddress.class), btcTxMock, mock(Address.class), Coin.SATOSHI);
        });
    }

    @Test
    public void testLogPeginBtc() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logPeginBtc(mock(RskAddress.class), btcTxMock, Coin.SATOSHI, 1);
        });
    }

    @Test
    public void testLogReleaseBtcRequested() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), btcTxMock, Coin.SATOSHI);
        });
    }

    @Test
    public void testLogRejectedPegin() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logRejectedPegin(btcTxMock, RejectedPeginReason.PEGIN_CAP_SURPASSED);
        });
    }

    @Test
    public void testLogUnrefundablePegin() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logUnrefundablePegin(btcTxMock, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
        });
    }

    @Test
    public void testLogReleaseBtcRequestReceived() {
        String sender = "0x00000000000000000000000000000000000000";
        String base58Address = "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn";
        Address btcDestinationAddress = Address.fromBase58(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), base58Address);
        Coin amount = Coin.COIN;
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logReleaseBtcRequestReceived(sender, btcDestinationAddress, amount);
        });
    }

    @Test
    public void testLogReleaseBtcRequestRejected() {
        String sender = "0x00000000000000000000000000000000000000";
        Coin amount = Coin.COIN;
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logReleaseBtcRequestRejected(sender, amount, reason);
        });
    }

    @Test
    public void logBatchPegoutCreated() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logBatchPegoutCreated(btcTxMock.getHash(), new ArrayList<>());
        });
    }

    @Test
    public void logPegoutConfirmed() {
        assertThrows(UnsupportedOperationException.class, () -> {
            eventLogger.logPegoutConfirmed(btcTxMock.getHash(), 5);
        });
    }

}
