package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class BridgeEventLoggerTest {

    private BridgeEventLogger eventLogger;
    private BtcTransaction btcTxMock;
    private Keccak256 rskTxHash;

    @Before
    public void setup() {
        eventLogger = spy(BridgeEventLogger.class);
        btcTxMock = mock(BtcTransaction.class);
        rskTxHash = PegTestUtils.createHash3(1);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogLockBtc() {
        eventLogger.logLockBtc(mock(RskAddress.class), btcTxMock, mock(Address.class), Coin.SATOSHI);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogPeginBtc() {
        eventLogger.logPeginBtc(mock(RskAddress.class), btcTxMock, Coin.SATOSHI, 1);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogReleaseBtcRequested() {
        eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), btcTxMock, Coin.SATOSHI);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogRejectedPegin() {
        eventLogger.logRejectedPegin(btcTxMock, RejectedPeginReason.PEGIN_CAP_SURPASSED);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogUnrefundablePegin() {
        eventLogger.logUnrefundablePegin(btcTxMock, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogReleaseBtcRequestReceived() {
        String sender = "0x00000000000000000000000000000000000000";
        String base58Address = "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn";
        Address btcDestinationAddress = Address.fromBase58(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), base58Address);
        Coin amount = Coin.COIN;

        eventLogger.logReleaseBtcRequestReceived(sender, btcDestinationAddress, amount);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLogReleaseBtcRequestRejected() {
        String sender = "0x00000000000000000000000000000000000000";
        Coin amount = Coin.COIN;
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;

        eventLogger.logReleaseBtcRequestRejected(sender, amount, reason);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void logBatchPegoutCreated() {
        eventLogger.logBatchPegoutCreated(btcTxMock.getHash(), new ArrayList<>());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void logPegoutConfirmed() {
        eventLogger.logPegoutConfirmed(btcTxMock.getHash(), 5);
    }

}
