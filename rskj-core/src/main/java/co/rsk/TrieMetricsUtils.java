package co.rsk;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class TrieMetricsUtils {



    private static Random random;

    public static List<String> compareRepositories(Repository repositoryA, Repository repositoryB){

        List<String> errorMessages = new ArrayList<>();


        if(ByteUtil.fastEquals(repositoryA.getRoot(), repositoryB.getRoot()) == false){
            errorMessages.add("RepositoryA.root != RepositoryB.root");
        }

        List<RskAddress> accountKeysB = repositoryB.getAccountsKeys().stream().collect(Collectors.toList());
        System.out.println("ACCOUNT KEYS B GOT");

        List<RskAddress> accountKeysA = repositoryA.getAccountsKeys().stream().collect(Collectors.toList());
        System.out.println("ACCOUNT KEYS A GOT");



        if(accountKeysA.size() != accountKeysB.size()){
            errorMessages.add("AccountKeysA != AccountKeysB");
        }

        for(int i = 0; i<accountKeysA.size(); i++){
            RskAddress accountA = accountKeysA.get(i);
            RskAddress accountB = accountKeysB.get(i);
            System.out.println("Evaluating AccountA ["+accountA+"] and Account B ["+accountB+"]");
            if(!accountA.equals(accountB)){
                errorMessages.add("AccountA ["+accountA+"] != AccountB ["+accountB+"]");
            }

            byte[] accountStateA = repositoryA.getAccountState(accountA).getEncoded();
            byte[] accountStateB = repositoryB.getAccountState(accountB).getEncoded();

            if(ByteUtil.fastEquals(accountStateA, accountStateB)==false){
                errorMessages.add("AccountStateA != AccountStateB");
            }


            if(ByteUtil.fastEquals(repositoryA.getRoot(), repositoryB.getRoot()) == false){
                errorMessages.add("RepositoryA Root != RepositoryB Root");
            }
        }

        if (errorMessages.isEmpty()){
            System.out.println("Both repositories are equals");
        }
        return errorMessages;

    }

    public static Coin randomCoin(int decimalZeros, int maxValue) {
        return new Coin(BigInteger.TEN.pow(decimalZeros).multiply(
                BigInteger.valueOf(getRandom().nextInt(maxValue))));
    }

    public static Random getRandom() {
        if (random == null){
            random = new Random(100);
        }

        return random;
    }

    public static BigInteger randomBigInteger(int maxSizeBytes) {
        return new BigInteger(maxSizeBytes*8,getRandom());
    }

    public static RskAddress randomAccountAddress() {
        byte[] bytes = new byte[20];

        getRandom().nextBytes(bytes);

        return new RskAddress(bytes);
    }

    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        getRandom().nextBytes(result);
        return result;
    }

    public static DataWord randomDataWord() {
        return DataWord.valueOf(randomBytes(32));
    }

    public static void deleteFile(String s) {

        File directory = new File(s);
        if(!directory.exists()){
            System.out.println("Directory does not exist.");
            System.exit(0);
        }else{
            delete(directory);
            System.out.println("Deleted ready");
        }
    }

    public static void delete(File file) {
        if(file.isDirectory()){

            //directory is empty, then delete it
            if(file.list().length==0){
                file.delete();
                //System.out.println("Directory is deleted : " + file.getAbsolutePath());
            }else{
                //list all the directory contents
                String files[] = file.list();
                for (String temp : files) {
                    //construct the file structure
                    File fileDelete = new File(file, temp);
                    //recursive delete
                    delete(fileDelete);
                }
                //check the directory again, if empty then delete it
                if(file.list().length==0){
                    file.delete();
                    //System.out.println("Directory is deleted : "+ file.getAbsolutePath());
                }
            }
        }else{
            //if file, then delete it
            file.delete();
            //System.out.println("File is deleted : " + file.getAbsolutePath());
        }
    }
}
