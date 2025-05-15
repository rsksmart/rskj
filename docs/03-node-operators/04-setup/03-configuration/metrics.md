### Exposing metrics from RSKj for JConsole and Prometheus

#### 1. Enable JMX for JConsole

Java Management Extensions (JMX) can be used to expose metrics for tools like JConsole.

1. Add the following JVM options when starting your RSKj node to enable JMX:

   ```shell
   -Dcom.sun.management.jmxremote
   -Dcom.sun.management.jmxremote.port=9010
   -Dcom.sun.management.jmxremote.authenticate=false
   -Dcom.sun.management.jmxremote.ssl=false
   ```

- Replace `9010` with the desired port number.
- For production environments, enable authentication and SSL for security.

2. Start your node with the above options and connect to it using JConsole:
- Open JConsole (`jconsole` command).
- Enter the hostname and port (e.g., `localhost:9010`).

Note: For more details on using JConsole, refer to the official documentation: [JConsole Documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/management/jconsole.html).

---

#### 2. Expose Metrics for Prometheus Using Prometheus JMX Exporter

To expose metrics for Prometheus, you can use the [Prometheus JMX Exporter](https://github.com/prometheus/jmx_exporter), which acts as a Java agent to scrape JMX metrics and expose them via an HTTP endpoint.

1. **Download the JMX Exporter**
   Download the `jmx_prometheus_javaagent` JAR file from the [releases page](https://github.com/prometheus/jmx_exporter/releases).

2. **Create a Configuration File**
   Create a `config.yaml` file to define the metrics you want to expose. Below is an example configuration:

   ```yaml
   rules:
     - pattern: ".*"
   ```

- This configuration exposes all available JMX metrics. You can customize it to include only specific metrics.

3. **Add the JMX Exporter as a Java Agent**
   Add the following JVM option when starting your RSKj node:

   ```shell
     -javaagent:/path/to/jmx_prometheus_javaagent.jar=8080:/path/to/config.yaml
   ```

- Replace `/path/to/jmx_prometheus_javaagent.jar` with the path to the downloaded jmx agent JAR file.
- Replace `8080` with the port where the metrics will be exposed.
- Replace `/path/to/config.yaml` with the path to the configuration file you created in the step 2.

4. **Run Prometheus**
   Configure Prometheus to scrape metrics from your node by adding the following to your `prometheus.yml`:

   ```yaml
   scrape_configs:
     - job_name: 'rskj'
       static_configs:
         - targets: ['localhost:8080'] # Replace it with the host and port of your node where metrics are exposed
   ```

---

#### 3. Verify Metrics

- **For JConsole**: Open JConsole and verify the metrics under the MBeans tab.
- **For Prometheus**: Access the metrics endpoint (e.g., `http://localhost:8080/metrics`) in your browser or use Prometheus to scrape the metrics.

### 4. Enable Emission of Custom JMX Metrics

RSKj provides the ability to emit custom JMX metrics defined in the `MetricKind` enum. To enable this feature, follow these steps:

1. **Enable Profiling in the Configuration**
   Update the `reference.conf` file or your custom configuration file to enable profiling:

   ```hocon
   system {
       profiling {
           enabled = true
       }
   }
   ```

   alternatively, you can set the following system property when starting the node:

   ```shell
     -Dsystem.profiling.enabled=true
   ```

2. **Verify Metrics in JConsole**
- Start your RSKj node with the `system.profiling.enabled=true` configuration.
- Open JConsole and connect to the node.
- Navigate to the `co.rsk.metrics.Jmx` MBean to view the custom metrics defined in the `MetricKind` enum.

3. **Access Metrics via Prometheus JMX Exporter**
   If you are using the Prometheus JMX Exporter, ensure that the exporter is configured to scrape the custom JMX metrics. The `config.yaml` file should include a rule to match the `co.rsk.metrics.Jmx` MBean:

   ```yaml
   rules:
     - pattern: "co\\.rsk\\.metrics\\.Jmx<.*>"
   ```

   or enable all metrics with `.*` pattern:

   ```yaml
   rules:
     - pattern: ".*"
   ```

   Restart the node with the JMX Exporter agent to expose the metrics for Prometheus.
