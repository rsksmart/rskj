package co.rsk.db.importer.provider;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import co.rsk.db.importer.provider.index.data.BootstrapDataSignature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BootstrapDataVerifierTest {

    @Test
    void verifyFileEmpty() {
        BootstrapDataVerifier bootstrapDataVerifier = new BootstrapDataVerifier();
        assertEquals(0, bootstrapDataVerifier.verifyEntries(new HashMap<>()));
    }

    @Test
    void verifyFileMany() {
        BootstrapDataVerifier bootstrapDataVerifier = new BootstrapDataVerifier();
        HashMap<String, BootstrapDataEntry> entries = new HashMap<>();
        List<String> keys = new ArrayList<>();
        keys.add("04330037e82c177d7108077c80440821e13c1c62105f85e030214b48d7b5dff0b8e7c158b171546a71139e4de56c8535c964514033b89a669a8e87a5e8770c147c");
        keys.add("0473602083afe175e7cae12dbc27da54ec5ac77f99920787f3e891e7af303aaed480770c0de4c991aea1712729260175e158fa73f63c60f0f1de057139c52714de");
        keys.add("04bf74915a14e96df0b520a659acc28ae21aada3b0415e35f82f0c7b546338fc48bbfdce858ce3e4690feeb443d7f4955881ba0b793999e4fef7e46732e7fedf02");

        String hash = "53cb8e93030183c5ba198433e8cd1f013f3d113e0f4d1756de0d1f124ead155a";

        String r1 = "8dba957877d5bdcb26d551dfa2fa509dfe3fe327caf0166130b9f467a0a0c249";
        String s1 = "dab3fdf2031515d2de1d420310c69153fcc356f22b50dfd53c6e13e74e346eee";
        String r2 = "f0e8aab4fdd83382292a1bbc5480e2ae8084dc245f000f4bc4534d383a3a7919";
        String s2 = "a30891f2176bd87b4a3ac5c75167f2442453c17c6e2fbfb36c3b972ee67a4c2d";
        String r3 = "00";
        String s3 = "00";

        entries.put(keys.get(0), new BootstrapDataEntry(1, "", "dbPath", hash, new BootstrapDataSignature(r1, s1)));
        entries.put(keys.get(1), new BootstrapDataEntry(1, "", "dbPath", hash, new BootstrapDataSignature(r2, s2)));
        entries.put(keys.get(2), new BootstrapDataEntry(1, "", "dbPath", hash, new BootstrapDataSignature(r3, s3)));

        assertEquals(2, bootstrapDataVerifier.verifyEntries(entries));
    }

    @Test
    void doNotVerifyForDifferentHashes() {
        BootstrapDataVerifier bootstrapDataVerifier = new BootstrapDataVerifier();
        HashMap<String, BootstrapDataEntry> entries = new HashMap<>();
        List<String> keys = new ArrayList<>();
        keys.add("04330037e82c177d7108077c80440821e13c1c62105f85e030214b48d7b5dff0b8e7c158b171546a71139e4de56c8535c964514033b89a669a8e87a5e8770c147c");
        keys.add("04dffaa346b18c26230d6050c6ab67f0761ab12136b0dc3a1f1c6ed31890ffac38baa3d8da1df4aa2acc72e45dc3599797ba668666c5395280217f2fd215262b8a");
        keys.add("04bf74915a14e96df0b520a659acc28ae21aada3b0415e35f82f0c7b546338fc48bbfdce858ce3e4690feeb443d7f4955881ba0b793999e4fef7e46732e7fedf02");

        String hash1 = "53cb8e93030183c5ba198433e8cd1f013f3d113e0f4d1756de0d1f124ead155a";
        String hash2 = "87d149cb424c0387656f211d2589fb5b1e16229921309e98588419ccca8a7362";
        String hash3 = "53cb8e93030183c5ba198433e8cd1f013f3d113e0f4d1756de0d1f124ead155c";

        String r1 = "8dba957877d5bdcb26d551dfa2fa509dfe3fe327caf0166130b9f467a0a0c249";
        String s1 = "dab3fdf2031515d2de1d420310c69153fcc356f22b50dfd53c6e13e74e346eee";
        String r2 = "0efa8e0738468e434e443676b5a91fe15b0c416ff7190d8d912dff65161ad33c";
        String s2 = "aae27edef80e137dc1a0e92b6ae7fed0d1dc1b4a0ea8930bad6abec5ca1297c1";
        String r3 = "00";
        String s3 = "00";

        entries.put(keys.get(0), new BootstrapDataEntry(1, "", "dbPath", hash1, new BootstrapDataSignature(r1, s1)));
        entries.put(keys.get(1), new BootstrapDataEntry(1, "", "dbPath", hash2, new BootstrapDataSignature(r2, s2)));
        entries.put(keys.get(2), new BootstrapDataEntry(1, "", "dbPath", hash3, new BootstrapDataSignature(r3, s3)));

        Assertions.assertThrows(BootstrapImportException.class, () -> bootstrapDataVerifier.verifyEntries(entries));
    }


}
