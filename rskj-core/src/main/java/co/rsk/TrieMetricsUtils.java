package co.rsk;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.vm.DataWord;

import java.io.File;
import java.math.BigInteger;
import java.util.Random;

public class TrieMetricsUtils {



    private static Random random;

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
