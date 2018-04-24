
## rskj-core

#### Compile, test and package

Run `$ ../gradlew build`

 - find jar artifacts at `build/libs`
 - find unit test and code coverage reports at `build/reports`

#### Run a RSK node

 - execute the `-all` jar in `build/libs` using `$ java -jar [jarfile]`.

#### Import sources into IntelliJ IDEA

Use IDEA 14 or better and import project based on Gradle sources.

Note that in order to build the project without errors in IDEA, you will need to run `gradle antlr4` manually.

#### Install artifacts into your local `~/.m2` repository

Run `../gradlew install`.

#### Publish rskj-core builds

Simply push to master, and [the Jenkins build](https://jenkins.rsk.co/) will take care of the rest.

