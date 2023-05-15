# Support library: Java Microbenchmark Harness (JMH)
We are using JMH library to perform our benchmarking. Please check [JMH Repo](https://github.com/openjdk/jmh) for more info about JMH and how to use it.

# Building Benchmarks

The Gradle task `jmhJar` is used to build the benchmark code, simply run it with `./gradlew jmhJar`.

# Running Benchmarks

There are 3 ways to run the benchmarks:

## Using Gradle task and Runners

The Gradle task is called `jmh`.

Runners are utility classes setting up and bootstrapping a Benchmark (or a set of them), they are implemented under `co.rsk.jmh.runners`.

In order to use a Runner, it must be provided as a Gradle parameter with `-Pbenchmark=<Runner>`. 

Some other useful and optional parameters exist. These arguments will be read by the Runners or loaded by the Benchmarks on those fields annotated with `@Param` and matching name. These parameters are optional at Gradle task level but might be required at Runner/Benchmark level, check them for more info. Current predefined fields are:
- **host**: specifying a _host_ for those benchmarks that require it (ie: they perform http requests)
- **config**: specifying a _config_ (see available options under `/resources` folder) for those benchmarks that require it

Examples of use: 
```
./gradlew jmh -Pbenchmark=BenchmarkWeb3E2ERunner -Phost=http://localhost:4444 -Pconfig=regtest
```
Uses convenient Runner `co.rsk.jmh.runners.BenchmarkWeb3E2ERunner`, providing the host `http://localhost:4444` and the config `regtest` that will be used by the Benchmarks.

```
./gradlew jmh -Pbenchmark=BenchmarkWeb3E2ERunner -Phost=http://localhost:7777 -Pconfig=testnet-3_860_000
```
Uses convenient Runner `co.rsk.jmh.runners.BenchmarkWeb3E2ERunner`, providing the host `http://localhost:7777` and the config `testnet-3_860_000` that will be used by the Benchmarks.

```
./gradlew jmh -Pbenchmark=BenchmarkWeb3E2ELocalWalletRunner -Phost=http://localhost:4444 -Pconfig=regtest
```
Uses convenient Runner `co.rsk.jmh.runners.BenchmarkWeb3E2ELocalWalletRunner`, providing the host `http://localhost:4444` and the config `regtest` that will be used by the Benchmarks.

## Using Gradle task but providing original JMH parameters 

The Gradle task is also `jmh`.

In order to know all the available JMH parameters simply run `java -jar <generated_jar> -h` to get the list and understand their usage.

Any of these parameters can be used with `jmh` Gradle task. To do so, provide a Gradle parameter `jmhArgs` which value will be a string containing all the original JMH parameters. As with vanilla JMH, it is possible to provide custom arguments with the `-p` prefix; these arguments will be then loaded by the Benchmarks on those fields annotated with `@Param` and matching name.

Please note that this mode is not using the convenient Runners.

Example of use: 
```
./gradlew jmh -PjmhArgs="-wi 5 -i 5 -f 1 -p suite=e2e -p host=http://localhost:4444 co.rsk.jmh.web3.BenchmarkWeb3"
```
This example is providing the JMH parameters `"-wi 5 -i 5 -f 1`, some custom ones `-p suite=e2e -p host=http://localhost:4444` and also specifying the Benchmark (class, not Runner) to be used `co.rsk.jmh.web3.BenchmarkWeb3`.

## Using the generated jar and original JMH parameters

You can generate the jar following the steps explained previously on this doc.

In order to know all the available JMH parameters simply run `java -jar <generated_jar> -h` to get the list and understand their usage.

Check [JMH Repo](https://github.com/openjdk/jmh) for more info on how to use this mode. 


