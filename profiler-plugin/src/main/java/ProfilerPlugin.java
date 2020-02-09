/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.spi.Plugin;
import co.rsk.spi.PluginService;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.listener.EthereumListenerAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


public class ProfilerPlugin implements Plugin {

    private PerformanceProfiler profiler;
    private OutputStream outputFile;
    private int blocksEvaluated;
    private FileMetricsOutput fileMetricsOutput;
    private PrometheusMetricsOutput prometheusMetricsOutput;

    @Override
    public void init() {
        profiler = new PerformanceProfiler();

        //fileMetricsOutput = new FileMetricsOutput("observations.csv");
        //profiler.registerConsumer(fileMetricsOutput::newObservation);

        prometheusMetricsOutput = new PrometheusMetricsOutput();
        profiler.registerConsumer(prometheusMetricsOutput::newObservation);
        ProfilerFactory.configure(profiler);
    }

    @Override
    public void load(Parameters parameters) {
        parameters.context().getCompositeEthereumListener().addListener(new EthereumListenerAdapter() {
            @Override
            public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
                if(++blocksEvaluated == 10000) {
                    System.exit(0);
                }
            }
        });

        parameters.registry().register(new PluginService() {
            @Override
            public void start() {
                System.out.println("Strapping ProfilerPlugin");
            }

            @Override
            public void stop() {
                try {
                    profiler.shutdown();
                    outputFile.close();
                } catch (IOException ex) {
                    throw new RuntimeException("Exception when stopping profiler", ex);
                }
            }
        });
    }
}