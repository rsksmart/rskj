package co.rsk.rpc.modules.eth.eip712;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class EIP712UtilsTest {

    private static JsonNode typedData;
    private static JsonNode recTypedData;
    private static JsonNode relayRequest;
    private static JsonNode funnyRequest;

    private EIP712Utils utils;

    @BeforeClass
    public static void setupClass() throws Exception {
        ObjectMapper m = new ObjectMapper();
        typedData = m.readTree(EIP712UtilsTest.class.getResourceAsStream("/eip712/typed_data.json"));
        recTypedData = m.readTree(EIP712UtilsTest.class.getResourceAsStream("/eip712/rec_typed_data.json"));
        relayRequest = m.readTree(EIP712UtilsTest.class.getResourceAsStream("/eip712/relay_request.json"));
        funnyRequest = m.readTree(EIP712UtilsTest.class.getResourceAsStream("/eip712/funny_data.json"));
    }

    @Before
    public void setup() {
        utils = new EIP712Utils();
    }

    @Test
    public void groupTypencodesOk() {
        assertThat(
                utils.encodeType("Group", types(typedData)),
                equalTo("Group(string name,Person[] members)Person(string name,address[] wallets)"));
    }

    @Test
    public void personTypeEncodesOk() {
        assertThat(
                utils.encodeType("Person", types(typedData)),
                equalTo("Person(string name,address[] wallets)"));
    }

    @Test
    public void personTypeHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashType("Person", types(typedData))),
                equalTo("fabfe1ed996349fc6027709802be19d047da1aa5d6894ff5f6486d92db2e6860"));
    }

    @Test
    public void fromDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.encodeData(
                        "Person",
                        typedData.with("message").get("from"),
                        types(typedData))),
                equalTo(String.join("",
                        "fabfe1ed996349fc6027709802be19d047da1aa5d6894ff5f6486d92db2e6860",
                        "8c1d2bd5348394761719da11ec67eedae9502d137e8940fee8ecd6f641ee1648",
                        "8a8bfe642b9fc19c25ada5dadfd37487461dc81dd4b0778f262c163ed81b5e2a"))
        );
    }

    @Test
    public void fromDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        "Person",
                        typedData.with("message").get("from"),
                        types(typedData))),
                equalTo("9b4846dd48b866f0ac54d61b9b21a9e746f921cefa4ee94c4c0a1c49c774f67f"));
    }

    @Test
    public void firstAddressInToDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.encodeData(
                        "Person",
                        typedData.with("message").withArray("to").get(0),
                        types(typedData))),
                equalTo(String.join("",
                        "fabfe1ed996349fc6027709802be19d047da1aa5d6894ff5f6486d92db2e6860",
                        "28cac318a86c8a0a6a9156c2dba2c8c2363677ba0514ef616592d81557e679b6",
                        "d2734f4c86cc3bd9cabf04c3097589d3165d95e4648fc72d943ed161f651ec6d"))
        );
    }

    @Test
    public void mailTypeEncodesOk() {
        assertThat(
                utils.encodeType("Mail", types(typedData)),
                equalTo("Mail(Person from,Person[] to,string contents)Person(string name,address[] wallets)"));
    }

    @Test
    public void mailTypeHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashType("Mail", types(typedData))),
                equalTo("4bd8a9a2b93427bb184aca81e24beb30ffa3c747e2a33d4225ec08bf12e2e753"));
    }

    @Test
    public void messageDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.encodeData(
                        typedData.get("primaryType").asText(),
                        typedData.with("message"),
                        types(typedData))),
                equalTo(String.join("",
                        "4bd8a9a2b93427bb184aca81e24beb30ffa3c747e2a33d4225ec08bf12e2e753",
                        "9b4846dd48b866f0ac54d61b9b21a9e746f921cefa4ee94c4c0a1c49c774f67f",
                        "ca322beec85be24e374d18d582a6f2997f75c54e7993ab5bc07404ce176ca7cd",
                        "b5aadf3154a261abdd9086fc627b61efca26ae5702701d05cd2305f7c52a2fc8"))
        );
    }

    @Test
    public void messagesDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        typedData.get("primaryType").asText(),
                        typedData.with("message"),
                        types(typedData))),
                equalTo("eb4221181ff3f1a83ea7313993ca9218496e424604ba9492bb4052c03d5c3df8"));
    }

    @Test
    public void domainDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        "EIP712Domain",
                        typedData.with("domain"),
                        types(typedData))),
                equalTo("f2cee375fa42b42143804025fc449deafd50cc031ca257e0b194a650a912090f"));
    }

    @Test
    public void typedDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.epi712encode_v4(typedData)),
                equalTo("a85c2e2b118698e88db68a8105b794a8cc7cec074e89ef991cb4f5f533819cc2"));
    }

    @Test
    public void rec_personEncodesOk() {
        assertThat(
                utils.encodeType("Person", types(recTypedData)),
                equalTo("Person(string name,Person mother,Person father)"));
    }

    @Test
    public void rec_personHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashType("Person", types(recTypedData))),
                equalTo("7c5c8e90cb92c8da53b893b24962513be98afcf1b57b00327ae4cc14e3a64116"));
    }

    @Test
    public void rec_motherDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.encodeData(
                        "Person",
                        recTypedData.with("message").get("mother"),
                        types(recTypedData))),
                equalTo(String.join("",
                        "7c5c8e90cb92c8da53b893b24962513be98afcf1b57b00327ae4cc14e3a64116",
                        "afe4142a2b3e7b0503b44951e6030e0e2c5000ef83c61857e2e6003e7aef8570",
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "88f14be0dd46a8ec608ccbff6d3923a8b4e95cdfc9648f0db6d92a99a264cb36"))
        );
    }

    @Test
    public void rec_motherDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        "Person",
                        recTypedData.with("message").get("mother"),
                        types(recTypedData))),
                equalTo("9ebcfbf94f349de50bcb1e3aa4f1eb38824457c99914fefda27dcf9f99f6178b"));
    }

    @Test
    public void rec_fatherDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.encodeData(
                        "Person",
                        recTypedData.with("message").get("father"),
                        types(recTypedData))),
                equalTo(String.join("",
                        "7c5c8e90cb92c8da53b893b24962513be98afcf1b57b00327ae4cc14e3a64116",
                        "b2a7c7faba769181e578a391a6a6811a3e84080c6a3770a0bf8a856dfa79d333",
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "02cc7460f2c9ff107904cff671ec6fee57ba3dd7decf999fe9fe056f3fd4d56e"))
        );
    }

    @Test
    public void rec_fatherDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        "Person",
                        recTypedData.with("message").get("father"),
                        types(recTypedData))),
                equalTo("b852e5abfeff916a30cb940c4e24c43cfb5aeb0fa8318bdb10dd2ed15c8c70d8"));
    }

    @Test
    public void rec_messageDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.encodeData(
                        recTypedData.get("primaryType").asText(),
                        recTypedData.with("message"),
                        types(recTypedData))),
                equalTo(String.join("",
                        "7c5c8e90cb92c8da53b893b24962513be98afcf1b57b00327ae4cc14e3a64116",
                        "e8d55aa98b6b411f04dbcf9b23f29247bb0e335a6bc5368220032fdcb9e5927f",
                        "9ebcfbf94f349de50bcb1e3aa4f1eb38824457c99914fefda27dcf9f99f6178b",
                        "b852e5abfeff916a30cb940c4e24c43cfb5aeb0fa8318bdb10dd2ed15c8c70d8"))
        );
    }

    @Test
    public void rec_messagesDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        recTypedData.get("primaryType").asText(),
                        recTypedData.with("message"),
                        types(recTypedData))),
                equalTo("fdc7b6d35bbd81f7fa78708604f57569a10edff2ca329c8011373f0667821a45"));
    }

    @Test
    public void rec_domainDataHashesOk() {
        assertThat(
                Hex.toHexString(utils.hashStruct(
                        "EIP712Domain",
                        recTypedData.with("domain"),
                        types(recTypedData))),
                equalTo("facb2c1888f63a780c84c216bd9a81b516fc501a19bae1fc81d82df590bbdc60"));
    }

    @Test
    public void rec_typedDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.epi712encode_v4(recTypedData)),
                equalTo("807773b9faa9879d4971b43856c4d60c2da15c6f8c062bd9d33afefb756de19c"));
    }

    @Test
    public void relayRequest_typedDataEncodesOk() {
        assertThat(
                Hex.toHexString(utils.epi712encode_v4(relayRequest)),
                equalTo("eb8d009317b0c884c025eea6c4e997c8b2dd7ff919ee7b06469b631c282dbf7d"));
    }

    @Test
    public void funnyRequest_doesNotThrow() {
        // Just test it doesn't throw
        utils.epi712encode_v4(funnyRequest);
    }

    private JsonNode types(JsonNode data) {
        return data.with("types");
    }
}
