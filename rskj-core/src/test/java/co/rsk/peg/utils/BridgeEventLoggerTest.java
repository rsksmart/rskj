package co.rsk.peg.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.pegin.RejectedPeginReason;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BridgeEventLoggerTest {
    private BridgeEventLogger eventLogger;
    private BtcTransaction btcTxMock;
    private Keccak256 rskTxHash;

    @BeforeEach
    void setup() {
        eventLogger = spy(BridgeEventLogger.class);
        btcTxMock = mock(BtcTransaction.class);
        rskTxHash = PegTestUtils.createHash3(1);
    }

    @Test
    void testLogLockBtc() {
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logLockBtc(
            mock(RskAddress.class),
            btcTxMock,
            mock(Address.class),
            Coin.SATOSHI
        ));
    }

    @Test
    void testLogPeginBtc() {
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logPeginBtc(
            mock(RskAddress.class),
            btcTxMock,
            Coin.SATOSHI,
            1
        ));
    }

    @Test
    void testLogReleaseBtcRequested() {
        byte[] rskTxHashBytes = rskTxHash.getBytes();
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logReleaseBtcRequested(
            rskTxHashBytes,
            btcTxMock,
            Coin.SATOSHI
        ));
    }

    @Test
    void testLogRejectedPegin() {
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logRejectedPegin(
            btcTxMock,
            RejectedPeginReason.PEGIN_CAP_SURPASSED
        ));
    }

    @Test
    void testLogUnrefundablePegin() {
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logUnrefundablePegin(
            btcTxMock,
            UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER
        ));
    }

    @Test
    void testLogReleaseBtcRequestReceived() {
        String sender = "0x00000000000000000000000000000000000000";
        String base58Address = "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn";
        Address btcDestinationAddress = Address.fromBase58(
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST), base58Address);
        Coin amount = Coin.COIN;
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logReleaseBtcRequestReceived(
            sender,
            btcDestinationAddress,
            amount
        ));
    }

    @Test
    void testLogReleaseBtcRequestRejected() {
        RskAddress sender = new RskAddress("0x0000000000000000000000000000000000000000");
        Coin amount = Coin.COIN;
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logReleaseBtcRequestRejected(
            sender,
            amount,
            reason
        ));
    }

    @Test
    void logBatchPegoutCreated() {
        Sha256Hash btcTxHash = btcTxMock.getHash();
        List<Keccak256> rskTxHashes = new ArrayList<>();
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logBatchPegoutCreated(
            btcTxHash,
            rskTxHashes
        ));
    }

    @Test
    void logPegoutConfirmed() {
        Sha256Hash btcTxHash = btcTxMock.getHash();
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logPegoutConfirmed(
            btcTxHash,
            5
        ));
    }

    @Test
    void logPegoutTransactionCreated() {
        Sha256Hash btcTxHash = btcTxMock.getHash();
        List<Coin> outpointValues = Arrays.asList(Coin.COIN, Coin.SATOSHI, Coin.FIFTY_COINS);
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logPegoutTransactionCreated(
            btcTxHash,
            outpointValues
        ));
    }
}
