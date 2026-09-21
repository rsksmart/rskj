Fragments for the generator to assemble live in this directory, one method
per file. This file is not one of them, and is here on purpose: the
generator parses every fragment as JSON, so anything it fails to skip
aborts generation. Removing the filter that skips it turns
CliToolsTest.generateOpenRpcDoc red rather than leaving a publish-critical
behaviour protected by review alone.
