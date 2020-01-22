package co.rsk;

import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.io.File;

public class SnappyMetrics {
    protected static final boolean READ = true;
    protected static final int MIN = 1;
    protected static final int MAX = 150000;
    protected boolean rw;
    protected int values;
    protected int seed;
    protected boolean useSnappy;
    protected RskContext objects;

    public SnappyMetrics(boolean rw, int values, int seed, boolean useSnappy, String path) {
        this.rw = rw;
        this.values = values;
        this.seed = seed;
        this.useSnappy = useSnappy;
        this.objects = new RskContext(new String[]{ "--testnet", "-base-path",  path}, useSnappy);
    }
}

class FileRecursiveDelete {
    public static void deleteFile(String s) {

        File directory = new File(s);
        if(!directory.exists()){
            System.out.println("Directory does not exist.");
            System.exit(0);
        }else{
            delete(directory);
//            System.out.println("Deleted ready");
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

    private static boolean compareBlockchains (BlockStore normalStore, BlockStore snappyStore) {
        boolean equals = normalStore.getMaxNumber() == snappyStore.getMaxNumber();

        long length = Math.min(normalStore.getMaxNumber(), snappyStore.getMaxNumber());
        for (int i = 1; i <= length && equals; i++ ) {
            Block normalBlock = normalStore.getChainBlockByNumber(i);
            Block snappyBlock = snappyStore.getChainBlockByNumber(i);
            equals &= normalBlock.getHash().equals(snappyBlock.getHash());

            if (i % 100 == 0) {
                System.out.println("Comparing block number " + i);
            }

        }
        return equals;
    }
}