# Security chain

## Verify authenticity of RSKj source code and its binary dependencies

TESTING SYNCHRONIZATION - Checking if it will update the PR

The authenticity of the source code must be verified by checking the signature of the release tags in the official Git repository. See [Reproducible builds](/node-operators/setup/reproducible-build/). The authenticity of the binary dependencies is verified by Gradle after following the steps below to install the necessary plugins.

### Download Rootstock Release Signing Key public key

For Linux based OS (Ubuntu for example), it's recommended to install `curl` and `gnupg-curl` in order to download the key through HTTPS.

We recommend using GPG v1 to download the public key because GPG v2 encounters problems when connecting to HTTPS key servers. You can also download the key using `curl`, `wget` or a web browser but always check the fingerprint before importing it.

```bash
gpg --keyserver https://secchannel.rsk.co/SUPPORT.asc --recv-keys A6DBEAC640C5A14B
```

You should see the output below:

```text
Output:
gpg: key A6DBEAC640C5A14B: "IOV Labs Support <support@iovlabs.org>" imported
gpg: Total number processed: 1
gpg: imported: 1  (RSA: 1)
```

## Verify the fingerprint of the public key

```bash
gpg --finger A6DBEAC640C5A14B
```

The output should look like this:

```text
Output:
pub   rsa4096 2022-05-11 [C]
1DC9 1579 9132 3D23 FD37  BAA7 A6DB EAC6 40C5 A14B
uid   [ unknown] IOV Labs Support <support@iovlabs.org>
sub   rsa4096 2022-05-11 [S]
sub   rsa4096 2022-05-11 [E]
```

## Verify the signature of SHA256SUMS.asc

The file`SHA256SUMS.asc` is signed with Rootstock public key and includes SHA256 hashes of the files necessary to start the build process.

_Note: Ensure to `cd` into the [`rskj`](https://github.com/rsksmart/rskj) directory_ before executing the commands below.

```bash
gpg --verify SHA256SUMS.asc 
```

The output should look like this:

```text
Output:
gpg: Signature made Wed May 11 10:50:48 2022 -03
gpg: using RSA key 1F1AA750373B90D9792DC3217997999EEA3A9079
gpg: Good signature from "IOV Labs Support <support@iovlabs.org>" [unknown]
gpg: WARNING: This key is not certified with a trusted signature!
gpg: There is no indication that the signature belongs to the owner.
Primary key fingerprint: 1DC9 1579 9132 3D23 FD37  BAA7 A6DB EAC6 40C5 A14B
Subkey fingerprint: 1F1A A750 373B 90D9 792D  C321 7997 999E EA3A 9079
```

*Note:* Learn more about [key management](https://www.gnupg.org/gph/en/manual/x334.html) here.

## Verification of binary dependencies

The authenticity of the script `configure.sh` is checked using the `sha256sum` command and the signed `SHA256SUM.asc` file. The script is used to download and check the authenticity of the Gradle Wrapper and Gradle Witness plugins. After these plugins are installed, the authenticity of the rest of the binary dependencies is checked by Gradle.

Linux - Windows (bash console)

<Tabs>
  <TabItem value="linux" label="Linux" default>
    ```bash
    sha256sum --check SHA256SUMS.asc
    ```
  </TabItem>
  <TabItem value="mac" label="Mac OSX">
   ```bash
  shasum --check SHA256SUMS.asc
   ```
  </TabItem>
</Tabs>

## Run configure script to configure secure environment

<Tabs>
  <TabItem value="linux" label="Linux, Mac OSX" default>
    ```bash
    ./configure.sh
    ```
  </TabItem>
</Tabs>