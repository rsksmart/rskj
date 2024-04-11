package co.rsk.util;

import co.rsk.PropertyGetter;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.db.RepositorySnapshot;
import co.rsk.rpc.Web3InformationRetriever;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;

public class ContractUtilTest {

    private final CallTransaction.Function CLAIM_FUNCTION = CallTransaction.Function.fromSignature(
            "claim",
            new String[]{"bytes32", "uint256", "address", "uint256"},
            new String[]{}
    );

    private final Constants testConstans = Constants.regtest();

    private final String EXPECTED_HASH = "0x3dc21e0a710489c951f29205f9961b2c311d48fdf5a35545469d7b43e88f7624";

    @Test
    public void testCalculateSwapHash() {
        Transaction mockedTx = mockTx();

        byte[] result = ContractUtil.calculateSwapHash(mockedTx, new ReceivedTxSignatureCache());

        assertEquals(EXPECTED_HASH, HexUtils.toJsonHex(result));
    }

    @Test
    public void testIsClaimTxAndValid() {
        Coin txCost = Coin.valueOf(5);
        SignatureCache signatureCache = new ReceivedTxSignatureCache();

        Transaction mockedTx = mockTx();
        Web3InformationRetriever mockedWeb3InformationRetriever = mock(Web3InformationRetriever.class);
        AccountInformationProvider mockedAccountInformationProvider = mock(AccountInformationProvider.class);

        when(mockedWeb3InformationRetriever.getInformationProvider("latest")).thenReturn(mockedAccountInformationProvider);
        when(mockedAccountInformationProvider.getBalance(any(RskAddress.class))).thenReturn(Coin.valueOf(3));
        when(mockedAccountInformationProvider.getStorageValue(any(RskAddress.class), any(DataWord.class)))
                .thenReturn(DataWord.valueOf(1));

        PropertyGetter propertyGetter = () -> mockedWeb3InformationRetriever;

        boolean result = ContractUtil.isClaimTxAndValid(mockedTx, txCost, testConstans, signatureCache, propertyGetter);

        assertTrue(result);
    }

    private Transaction mockTx() {
        byte[] senderBytes = Hex.decode("0000000000000000000000000000000001000001");
        RskAddress claimAddress = new RskAddress(senderBytes);

        byte[] preimage = "preimage".getBytes(StandardCharsets.UTF_8);
        byte[] callData = CLAIM_FUNCTION.encode(preimage,
                10,
                "0000000000000000000000000000000001000002",
                1000000);

        Transaction mockedTx = mock(Transaction.class);

        when(mockedTx.getSender(any(SignatureCache.class))).thenReturn(claimAddress);
        when(mockedTx.getNonce()).thenReturn(ByteUtil.cloneBytes(BigInteger.ZERO.toByteArray()));
        when(mockedTx.getGasPrice()).thenReturn(Coin.valueOf(1));
        when(mockedTx.getGasLimit()).thenReturn(BigInteger.valueOf(1).toByteArray());
        when(mockedTx.getReceiveAddress()).thenReturn(new RskAddress(testConstans.getEtherSwapContractAddress()));
        when(mockedTx.getData()).thenReturn(callData);
        when(mockedTx.getValue()).thenReturn(Coin.ZERO);

        return mockedTx;
    }
}
