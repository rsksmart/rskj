package co.rsk.jmh.runners;

import co.rsk.jmh.helpers.OptionsHelper;
import co.rsk.jmh.web3.LogIndexBenchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.Options;

public class BenchmarkLogIndexRunner {

    public static void main(String[] args) throws CommandLineOptionException, RunnerException {
        Options opt = OptionsHelper.createE2EBuilder(args, "results.csv")
                .include(LogIndexBenchmark.class.getName())
                .build();
        new Runner(opt).run();
    }
}
