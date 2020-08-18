package co.rsk.rpc.modules.eth;

import co.rsk.core.Wallet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(MockitoJUnitRunner.class)
public class EthModuleWalletEnabledTest {

    @Mock
    Wallet wallet;

    private JsonNode readJson(String path) throws Exception {
        return new ObjectMapper().readTree(getClass().getResourceAsStream(path));
    }

    private Account getAccount(String privKeyStr) {
        ECKey privKey = ECKey.fromPrivate(HashUtil.keccak256(privKeyStr.getBytes(StandardCharsets.UTF_8)));
        System.out.println(Hex.toHexString(privKey.getPrivKeyBytes()));
        return new Account(privKey);
    }

    @Test
    public void testSignTyped() throws Exception {
        Account account = getAccount("cow");
        when(wallet.getAccount(any())).thenReturn(account);
        JsonNode typedData = readJson("/eip712/typed_data.json");
        String signature = new EthModuleWalletEnabled(wallet)
                .signTypedData(account.getAddress().toHexString(), typedData);
        assertThat(signature, equalTo(
                "0x65cbd956f2fae28a601bebc9b906cea0191744bd4c4247bcd27cd08f8eb6b71c78efdf7a31dc9abee78f492292721f362d296cf86b4538e07b51303b67f749061b"));
    }

    @Test
    public void rec_testSignTyped() throws Exception {
        Account account = getAccount("dragon");
        when(wallet.getAccount(any())).thenReturn(account);
        JsonNode typedData = readJson("/eip712/rec_typed_data.json");
        String signature = new EthModuleWalletEnabled(wallet)
                .signTypedData(account.getAddress().toHexString(), typedData);
        assertThat(signature, equalTo(
                "0xf2ec61e636ff7bb3ac8bc2a4cc2c8b8f635dd1b2ec8094c963128b358e79c85c5ca6dd637ed7e80f0436fe8fce39c0e5f2082c9517fe677cc2917dcd6c84ba881c"));
    }

    @Test
    public void relayRequest_testSignTyped() throws Exception {
        Account account = getAccount("dragon");
        when(wallet.getAccount(any())).thenReturn(account);
        JsonNode typedData = readJson("/eip712/relay_request.json");
        String signature = new EthModuleWalletEnabled(wallet)
                .signTypedData(account.getAddress().toHexString(), typedData);
        assertThat(signature, equalTo(
                "0xd54ed4b0f72c86a68729940a329342a854909d1fb91c5ccb0e8482e59921e7a9172a62243f0e0bfaffe3498c5a24e21df823f33a56b02628300e4f34addcdba11c"));
    }
}
