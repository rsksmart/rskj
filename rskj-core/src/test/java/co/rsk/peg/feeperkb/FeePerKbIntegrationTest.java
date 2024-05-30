package co.rsk.peg.feeperkb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.storage.StorageAccessor;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeePerKbIntegrationTest {
    private final FeePerKbConstants feePerKbConstants = FeePerKbMainNetConstants.getInstance();
    private final StorageAccessor inMemoryStorage = new InMemoryStorage();
    private FeePerKbSupport feePerKbSupport;
    private FeePerKbStorageProvider feePerKbStorageProvider;

    @BeforeEach
    void setUp() {
        feePerKbStorageProvider = new FeePerKbStorageProviderImpl(inMemoryStorage);
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants,feePerKbStorageProvider);
    }

    @Test
    void voteFeePerKbChange_winnerFeePerKbValue_shouldReturnSuccessfulFeeVotedResponseCode() {
        SignatureCache signatureCache = mock(SignatureCache.class);

        //First Vote
        Transaction firstTx = getTransactionFromAuthorizedCaller(signatureCache, 1);
        Coin firstFeePerKbVote = Coin.valueOf(50_000L);
        feePerKbSupport.voteFeePerKbChange(firstTx, firstFeePerKbVote, signatureCache);

        //Second vote
        Transaction secondTx = getTransactionFromAuthorizedCaller(signatureCache, 2);
        Coin secondFeePerKbVote = Coin.valueOf(50_000L);
        Integer ActualResult = feePerKbSupport.voteFeePerKbChange(secondTx, secondFeePerKbVote, signatureCache);

        Integer expectedResult = FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode();
        assertEquals(expectedResult, ActualResult);
    }

    private Transaction getTransactionFromAuthorizedCaller(SignatureCache signatureCache, int authorizedRskAddressIndex) {
        RskAddress authorizedRskAddress = this.getAuthorizedRskAddresses().get(authorizedRskAddressIndex);
        Transaction txFromAuthorizedCaller = mock(Transaction.class);
        when(txFromAuthorizedCaller.getSender(signatureCache)).thenReturn(authorizedRskAddress);

        return txFromAuthorizedCaller;
    }

    private List<RskAddress> getAuthorizedRskAddresses(){
        return Stream.of(
            "a02db0ed94a5894bc6f9079bb9a2d93ada1917f3",
            "180a7edda4e640ea5a3e495e17a1efad260c39e9",
            "8418edc8fea47183116b4c8cd6a12e51a7e169c1"
        ).map(RskAddress::new).collect(Collectors.toList());
    }
}
