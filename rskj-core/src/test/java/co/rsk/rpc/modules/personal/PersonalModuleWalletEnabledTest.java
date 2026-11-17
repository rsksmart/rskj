package co.rsk.rpc.modules.personal;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.TransactionPool;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.crypto.ECKey;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.HexKeyParam;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by Nazaret García on 15/01/2021
 */

class PersonalModuleWalletEnabledTest {
    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    void importRawKey_KeyIsNull_ThrowsNullPointerException() {
        PersonalModuleWalletEnabled personalModuleWalletEnabled = createPersonalModuleWalletEnabled(null);
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> personalModuleWalletEnabled.importRawKey(null, "passphrase1"));
    }

    @Test
    void importRawKey_KeyContains0xPrefix_OK() {
        ECKey eckey = new ECKey();
        String rawKey = ByteUtil.toHexString(eckey.getPrivKeyBytes());
        String passphrase = "passphrase1";
        byte[] hexDecodedKey = Hex.decode(rawKey);

        RskAddress addressMock = mock(RskAddress.class);
        doReturn("{}").when(addressMock).toJsonString();

        Wallet walletMock = mock(Wallet.class);
        doReturn(addressMock).when(walletMock).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        doReturn(true).when(walletMock).unlockAccount(eq(addressMock), eq(passphrase), any(Long.class));

        PersonalModuleWalletEnabled personalModuleWalletEnabled = createPersonalModuleWalletEnabled(walletMock);
        HexKeyParam hexKeyParam = new HexKeyParam(String.format("0x%s", rawKey));
        String result = personalModuleWalletEnabled.importRawKey(hexKeyParam, passphrase);

        verify(walletMock, times(1)).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        verify(walletMock, times(1)).unlockAccount(addressMock, passphrase, 1800000L);
        verify(addressMock, times(1)).toJsonString();

        assertEquals("{}", result);
    }

    @Test
    void importRawKey_KeyDoesNotContains0xPrefix_OK() {
        ECKey eckey = new ECKey();
        String rawKey = ByteUtil.toHexString(eckey.getPrivKeyBytes());
        String passphrase = "passphrase1";
        byte[] hexDecodedKey = Hex.decode(rawKey);

        RskAddress addressMock = mock(RskAddress.class);
        doReturn("{}").when(addressMock).toJsonString();

        Wallet walletMock = mock(Wallet.class);
        doReturn(addressMock).when(walletMock).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        doReturn(true).when(walletMock).unlockAccount(eq(addressMock), eq(passphrase), any(Long.class));

        PersonalModuleWalletEnabled personalModuleWalletEnabled = createPersonalModuleWalletEnabled(walletMock);
        HexKeyParam hexKeyParam = new HexKeyParam(rawKey);
        String result = personalModuleWalletEnabled.importRawKey(hexKeyParam, passphrase);

        verify(walletMock, times(1)).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        verify(walletMock, times(1)).unlockAccount(addressMock, passphrase, 1800000L);
        verify(addressMock, times(1)).toJsonString();

        assertEquals("{}", result);
    }

    @Test
    void sendTransaction_type2PriorityFeeAboveMaxFee_throwsInvalidParams() {
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount("passphrase1");
        Ethereum eth = mock(Ethereum.class);
        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(config, eth, wallet, mock(TransactionPool.class));

        CallArguments args = new CallArguments();
        args.setFrom(sender.toJsonString());
        args.setTo(new RskAddress(new ECKey().getAddress()).toJsonString());
        args.setGas("0x5208");
        args.setValue("0x0");
        args.setNonce("0x1");
        args.setType("0x2");
        args.setChainId(HexUtils.toQuantityJsonHex(config.getNetworkConstants().getChainId()));
        args.setMaxPriorityFeePerGas("0x77359400");
        args.setMaxFeePerGas("0x3b9aca00");
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(RskJsonRpcRequestException.class,
                () -> personalModuleWalletEnabled.sendTransaction(argsParam, "passphrase1"));
        assertEquals(-32602, ex.getCode());
        assertEquals("Invalid transaction: maxPriorityFeePerGas (2000000000) must not exceed maxFeePerGas (1000000000)", ex.getMessage());
        verify(eth, never()).submitTransaction(any());
    }

    @Test
    void sendTransaction_accessListAddressNotHex_throwsInvalidParams() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress("0xzz" + "00".repeat(19));
        entry.setStorageKeys(List.of());

        assertSendTransactionInvalidParams("0x1", args -> args.setAccessList(List.of(entry)),
                "Access list entry address must be a 20-byte hex value at index 0");
    }

    @Test
    void sendTransaction_authorizationListRNotHex_throwsInvalidParams() {
        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setChainId("0x21");
        entry.setAddress("0x" + "11".repeat(20));
        entry.setNonce("0x0");
        entry.setYParity("0x0");
        entry.setR("0xzz");
        entry.setS("0x1");

        assertSendTransactionInvalidParams("0x4", args -> args.setAuthorizationList(List.of(entry)),
                "Authorization list entry r must be hex at index 0");
    }

    private void assertSendTransactionInvalidParams(
            String type, Consumer<CallArguments> customizer, String expectedMessage) {
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount("passphrase1");
        Ethereum eth = mock(Ethereum.class);
        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(config, eth, wallet, mock(TransactionPool.class));
        CallArguments args = TransactionFactoryHelper.createArguments(sender, new RskAddress(new ECKey().getAddress()));
        args.setType(type);
        args.setChainId(HexUtils.toQuantityJsonHex(config.getNetworkConstants().getChainId()));
        customizer.accept(args);
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(RskJsonRpcRequestException.class,
                () -> personalModuleWalletEnabled.sendTransaction(argsParam, "passphrase1"));
        assertEquals(-32602, ex.getCode());
        assertEquals(expectedMessage, ex.getMessage());
        verify(eth, never()).submitTransaction(any());
    }

    @Test
    void sendTransaction_pendingStateLookupFails_isNotReportedAsInvalidParams() {
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount("passphrase1");
        TransactionPool transactionPool = mock(TransactionPool.class);
        when(transactionPool.getPendingState()).thenThrow(new IllegalArgumentException("The trie with root 0xabc is missing in this store"));
        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(config, mock(Ethereum.class), wallet, transactionPool);
        CallArguments args = TransactionFactoryHelper.createArguments(sender, new RskAddress(new ECKey().getAddress()));
        args.setNonce(null);
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        Assertions.assertThrows(IllegalArgumentException.class, () -> personalModuleWalletEnabled.sendTransaction(argsParam, "passphrase1"));
    }

    @Test
    void sendTransaction_submitFailsWithIllegalArgument_isNotReportedAsInvalidParams() {
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount("passphrase1");
        Ethereum eth = mock(Ethereum.class);
        when(eth.submitTransaction(any())).thenThrow(new IllegalArgumentException("The trie with root 0xabc is missing in this store"));
        PersonalModuleWalletEnabled personalModuleWalletEnabled = new PersonalModuleWalletEnabled(config, eth, wallet, mock(TransactionPool.class));
        CallArguments args = TransactionFactoryHelper.createArguments(sender, new RskAddress(new ECKey().getAddress()));
        CallArgumentsParam argsParam = TransactionFactoryHelper.toCallArgumentsParam(args);

        Assertions.assertThrows(IllegalArgumentException.class, () -> personalModuleWalletEnabled.sendTransaction(argsParam, "passphrase1"));
    }

    private PersonalModuleWalletEnabled createPersonalModuleWalletEnabled(Wallet wallet) {
        return new PersonalModuleWalletEnabled(config, null, wallet, null);
    }

}
