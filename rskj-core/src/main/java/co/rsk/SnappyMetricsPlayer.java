package co.rsk;


import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(description = "Run experimentrs", name = "play", mixinStandardHelpOptions = true,
        version = "play 1.0")
class SnappyMetricsPlay implements Callable<Void> {

    @CommandLine.Option(names = {"-srcNormal", "--sourceNormal"}, description = "Directory with the Blockchain instance.")
    private String path = "/Users/julian/workspace/rskj-projects/dbs/normal-database-150"; //snappyBlockchainDir = "/Users/julian/workspace/rskj-projects/dbs/snappy-database-150";

    @CommandLine.Option(names = {"-v", "--values"}, description = "Quantity of blocks to r/w")
    private int values = 3000;

    @CommandLine.Option(names = {"-sp", "--snappy"}, description = "Use snappy or not")
    private boolean useSnappy = false;

    @CommandLine.Option(names = {"-rw", "--readwrite"}, description = "Read/Write (true/false)")
    private boolean rW = true;

    @CommandLine.Option(names = {"-sd", "--seed"}, description = "Seed")
    private int seed = 100;

    public static void main(String[] args){
//        CommandLine.call(new SnappyMetricsPlay(), args);
        SnappyMetrics sMetrics = new SnappyMetrics("/Users/julian/workspace/rskj-projects/dbs/normal-database-150", false, 50, 100, false);
        final long totalTime = sMetrics.runExperiment();
        System.out.println(totalTime);
        System.gc();
        System.exit(0);
    }

    @Override
    public Void call() {
        SnappyMetrics sMetrics = new SnappyMetrics(path, rW, values, seed, useSnappy);
        sMetrics.runExperiment();
        System.gc();
        return null;
    }

}