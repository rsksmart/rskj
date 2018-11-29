package co.rsk.metrics.block.builder;

import co.rsk.config.TestSystemProperties;
import org.ethereum.core.Genesis;
import org.ethereum.crypto.Keccak256Helper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Vector;



public final class GenesisLoader {
    private TestSystemProperties config;
    private GenesisInfo genesisInfo;

    public static GenesisLoader newGenesisLoader(TestSystemProperties config, String root) throws  InvalidGenesisFileException{
        GenesisLoader loader = new GenesisLoader(config, root);
        return  loader;
    }


    public Vector<AccountStatus> getRegularAccounts(){
        return this.genesisInfo.getRegularAccounts();
    }

    public Vector<AccountStatus> getRemascCoinbases() { return  this.genesisInfo.getRemascCoinbases(); }

    public Vector<AccountStatus> getTokenContracts(){
        return this.genesisInfo.getTokenContracts();
    }

    public AccountStatus getTokensOwner(){
        return this.genesisInfo.getTokensOwner();
    }



    public Genesis loadGenesis() {

        BigInteger initialNonce = config.getBlockchainConfig().getCommonConstants().getInitialNonce();

        return org.ethereum.core.genesis.GenesisLoader.loadGenesis(config, "rsk-block-performance-test.json", initialNonce, true);
    }


    private GenesisLoader(TestSystemProperties config, String root) throws InvalidGenesisFileException {
        try{

            this.config = config;
            loadGenesisInfo(root);

            //Check if genesis file has been tampered with
            byte[] genesisHash = this.genesisInfo.getGenesisFileHash();
            Path path = Paths.get(root + "/rsk-block-performance-test.json");
            byte[] fileHash = Keccak256Helper.keccak256(Files.readAllBytes(path));

            if(!Arrays.equals(genesisHash, fileHash)){
                throw new InvalidGenesisFileException("Genesis json file has been manually altered, please regenerate using GenesisBuilder");
            }
        }
         catch (IOException e) {
            throw new InvalidGenesisFileException(e);
        }
    }


    private void loadGenesisInfo(String root) throws IOException {
        try {
            FileInputStream fis = new FileInputStream(root + "/genesis-info");
            ObjectInputStream ois = new ObjectInputStream(fis);
            genesisInfo = (GenesisInfo) ois.readObject();
            ois.close();
            fis.close();
        }
        catch (ClassNotFoundException e){
            throw new IOException(e);
        }
    }
}
