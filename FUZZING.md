# RSKj Fuzzing Initiative
# Overview
The RSKj Fuzzing Initiative is an ongoing effort to improve the security and reliability of the RSKj codebase through fuzz testing. The project is built on top of the open-source version of the [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer/) fuzzer, which is based on the [LibFuzzer](https://llvm.org/docs/LibFuzzer.html) engine. The main goal of the initiative is to identify fuzzing candidates in components of the RSKj platform that could mostly benefit from such tests, and implement individual fuzz targets for each.

The fuzzer currently targets 17 candidate classes across the org.ethereum and co.rsk packages, with a total of 48 fuzz targets. As the project continues to evolve, enhanced code coverage and more tailored test cases will be created, seeking to uncover bugs that could lead to security issues or performance bottlenecks.

Running all fuzz targets:
```bash
$ ./configure.sh
$ JAZZER_FUZZ=1 ./gradlew runAllFuzzTests --info --continue
```

To run individual fuzz targets, use the `fuzzTest` task and point to the target's package path:
```bash
$  JAZZER_FUZZ=1 ./gradlew fuzzTest --tests org.ethereum.util.ByteUtilFuzzTest --info --continue
```

When done, the RSKj fuzzer will display the paths to tests and coverage reports.
