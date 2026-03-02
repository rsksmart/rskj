get stream string.js "ekkarat.w@gmail.com"
<component name="ProjectCodeStyleConfiguration">
  <code_scheme name="Project" version="173">
    <codeStyleSettings language="JAVA">
      <option name="ALIGN_MULTILINE_PARAMETERS_IN_CALLS" value="true" />
      <option name="CALL_PARAMETERS_WRAP" value="5" />
      <option name="CALL_PARAMETERS_LPAREN_ON_NEXT_LINE" value="true" />
      <option name="CALL_PARAMETERS_RPAREN_ON_NEXT_LINE" value="true" />
      <option name="METHOD_PARAMETERS_WRAP" value="5" />
      <option name="METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE" value="true" />
      <option name="IF_BRACE_FORCE" value="3" />
      <option name="DOWHILE_BRACE_FORCE" value="3" />
      <option name="WHILE_BRACE_FORCE" value="3" />
      <option name="FOR_BRACE_FORCE" value="3" />
    </codeStyleSettings>
  </code_scheme>
</component>
## rskj-core

#### Configure the RSK-compatible Gradle local installation

Using a local Gradle installation will be necessary to be able to run `./gradlew` commands while avoiding the `Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain` error.

##### Linux and macOS

Run `sh configure.sh` in the project's root directory.

##### Windows

Run `./configure.sh` in the project's root directory, using Windows 10's git bash. You could also run `bash configure.sh` under WSL.

#### Compile, test and package

Run `$ ./gradlew build`

 - find jar artifacts at `build/libs`
 - find unit test and code coverage reports at `build/reports`

#### Run a RSK node

 - execute the `-all` jar in `build/libs` using `$ java -jar [jarfile]`.

#### Import sources into IntelliJ IDEA

Use IDEA 14 or better and import project based on Gradle sources.

Note that in order to build the project without errors in IDEA, you will need to run `gradle antlr4` manually.

#### Install artifacts into your local `~/.m2` repository

Run `./gradlew install`.

#### Publish rskj-core builds

Simply push to master, and [the Jenkins build](https://jenkins.rsk.co/) will take care of the rest.

