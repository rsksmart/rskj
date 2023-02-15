package co.rsk.pcc;

import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.util.HexUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.Arrays;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class InstallCodeDSLTest {
    private World world;
    private WorldDslProcessor processor;

    @BeforeEach
    void setup() {
        this.world = new World();
        this.processor = new WorldDslProcessor(world);

    }

    @Test
    void signTest() throws IOException, DslProcessorException, DecoderException {
        processor.processCommands(DslParser.fromResource("dsl/pcc/setup.txt"));
        final Account acc1 = world.getAccountByName("acc1");
        byte[] account = Arrays.concatenate(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0}, acc1.getAddress().getBytes());
        byte[] nonce = new byte[32];
        nonce[31]=1;
        final InputStream isBytecode = ClassLoader.getSystemClassLoader().getResourceAsStream("dsl/pcc/installcode.bytecode");
        String code = new BufferedReader(
                new InputStreamReader(isBytecode))
                .lines()
                .collect(Collectors.joining("\n"));
        final byte[] codeBytes = Hex.decodeHex(code);
        byte[] hash = InstallCode.getHashToSignFromCode(account, nonce, codeBytes);
        System.out.println("H:" + Hex.encodeHexString(hash)); //bd77c79c1808e3c2fdb12f3271f8371d0ff1d8c783828debd37975141bb8c2a5
        ECKey.ECDSASignature sign = acc1.getEcKey().doSign(hash);
        ECDSASignature signature = ECDSASignature.fromComponentsWithRecoveryCalculation(sign.r.toByteArray(), sign.s.toByteArray(), hash, acc1.getEcKey().getPubKey());
        byte[] rByte = signature.getR().toByteArray();
        byte[] sByte = signature.getS().toByteArray();
        byte[] vByte = Arrays.concatenate(new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
                new byte[]{signature.getV()});
        System.out.print("Data: ");
        System.out.print(Hex.encodeHexString(account)); //000000000000000000000000a0663f719962ec10bb57865532bef522059dfd96
        System.out.print(Hex.encodeHexString(vByte)); //0000000000000000000000000000000000000000000000000000000000000000
        System.out.print(Hex.encodeHexString(rByte)); //690024a1e337e0c93732568368dacbb97f95f15c7aba5da6da8ee0ab645f55a8
        System.out.print(Hex.encodeHexString(sByte)); //7812856637de63509ace6a712f3bcdba1346d6573693e177c2d703e0ce1cf7cb
        System.out.println(code); //36303830363034303532333438303135363130303130353736303030383066643562353036313031353038303631303032303630303033393630303066336665363038303630343035323334383031353631303031303537363030303830666435623530363030343336313036313030333635373630303033353630653031633830363332653634636563313134363130303362353738303633363035373336316431343631303035393537356236303030383066643562363130303433363130303735353635623630343035313631303035303931393036313030643935363562363034303531383039313033393066333562363130303733363030343830333630333831303139303631303036653931393036313030396435363562363130303765353635623030356236303030383035343930353039303536356238303630303038313930353535303530353635623630303038313335393035303631303039373831363130313033353635623932393135303530353635623630303036303230383238343033313231353631303062333537363130306232363130306665353635623562363030303631303063313834383238353031363130303838353635623931353035303932393135303530353635623631303064333831363130306634353635623832353235303530353635623630303036303230383230313930353036313030656536303030383330313834363130306361353635623932393135303530353635623630303038313930353039313930353035363562363030303830666435623631303130633831363130306634353635623831313436313031313735373630303038306664356235303536666561323634363937303636373335383232313232303264393133316538666237323463656263316536373730336466366632653931356164353033323265653336393863383363373532343965393233653264353036343733366636633633343330303038303730303333
    }

    /**
     * account1 Installs code in its own EOA (via InstallCode), and then acc2 calls a contract
     * that invokes a method on acc1 and checks for success.
     * */
    @Test
    public void installCodeViaPrecAndCallEOAViaContractCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/pcc/installcode.txt");
        processor.processCommands(parser);

        Account acc1 = world.getAccountByName("acc1");
        acc1.getAddress();

        RepositorySnapshot snapshot = world.getRepositoryLocator()
                .findSnapshotAt(world.getBlockByName("b02").getHeader())
                .get();

        byte[] code = snapshot.getCode(acc1.getAddress());
        assertNotNull(code);
        assertTrue(code.length  > 0);
        AccountState state = snapshot.getAccountState(acc1.getAddress());
        assertTrue(state.isSmart());
    }
}
