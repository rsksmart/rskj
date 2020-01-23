import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class PrometheusMetricsOutput implements MetricsOutput {

    private final Map<String, Summary> summary;
    private HTTPServer server;

    public PrometheusMetricsOutput() {
        summary = new HashMap<>();
        try {
            server = new HTTPServer(8000);
        } catch (IOException e) {
            System.out.println("Prometheus server could not be initialied");
        }
    }

    @Override
    public void newObservation(Observation observation) {
        summary.computeIfAbsent(observation.getCategory(), k -> Summary.build()
                .quantile(0.95, 0.01).help("dunno").name(k).register());
        summary.get(observation.getCategory()).observe((double) observation.getDuration().toMillis());
    }
}
