package co.rsk;


import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@CommandLine.Command(description = "Run experimentrs", name = "play", mixinStandardHelpOptions = true,
        version = "play 1.0")
class SnappyMetricsPlay implements Callable<Void> {

    @Option(names = {"-srcNormal"}, description = "Directory with the Blockchain instance.")
    private String normalPath = "/Users/julian/workspace/rskj-projects/dbs/normal-database-150"; //snappyBlockchainDir = "/Users/julian/workspace/rskj-projects/dbs/snappy-database-150";

    @Option(names = {"-srcSnappy"}, description = "Directory with the Blockchain instance.")
    private String snappyPath = "/Users/julian/workspace/rskj-projects/dbs/snappy-database-150";

    @Option(names = {"-v", "--values"}, description = "Quantity of blocks to r/w")
    private int values = 3000;

    @Option(names = {"-st", "--store"}, description = "Kind of store (b/r/sr/w)")
    private String store = "b";

    @Option(names = {"-sp", "--snappy"}, description = "Use snappy or not")
    private boolean useSnappy = false;

    @Option(names = {"-rw", "--readwrite"}, description = "Read/Write (true/false)")
    private boolean rW = true;

    @Option(names = {"-sd", "--seed"}, description = "Seed")
    private int seed = 100;

    public static void main(String[] args){
        CommandLine.call(new SnappyMetricsPlay(), args);
//        SnappyMetrics sMetrics = new SnappyMetrics("/Users/julian/workspace/rskj-projects/dbs/snappy-database-150", false, 1000, 100, true);
//        sMetrics.runExperiment();
        System.gc();
        System.exit(0);
    }

    @Override
    public Void call() {
        String path;
        if (useSnappy) {
            path = snappyPath;
        } else {
            path = normalPath;
        }

        SnappyBlocksMetrics sMetrics = null;

        if (store.equals("b")) {
            sMetrics = new SnappyBlocksMetrics(path, rW, values, seed, useSnappy);
        } else if (store.equals("r")) {

        } else if (store.equals("sr")) {

        } else if (store.equals("w")) {

        } else {
            return null;
        }

        final long time = sMetrics.runExperiment();
//        System.out.println(time);
        System.gc();
        return null;
    }

}