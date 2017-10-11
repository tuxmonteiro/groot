/*
 * Copyright (c) 2017-2017 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.groot.monit;

import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.monit.collectors.MetricsCollector;
import com.globocom.grou.groot.monit.collectors.MetricsCollectorByScheme;
import io.galeb.statsd.StatsDClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.Response;
import org.asynchttpclient.exception.TooManyConnectionsException;
import org.asynchttpclient.exception.TooManyConnectionsPerHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonitorService {

    private static final Log LOGGER = LogFactory.getLog(MonitorService.class);

    private static final String UNKNOWN = "UNKNOWN";

    private final String prefixTag = SystemEnv.PREFIX_TAG.getValue();
    private final AtomicReference<Test> test = new AtomicReference<>(null);
    private final String hostnameFormated = SystemInfo.hostname().replaceAll("[.]", "_");
    private final Object lock = new Object();

    private final StatsDClient statsdClient;
    private volatile int delta = 0;
    private List<MetricsCollector> targets = Collections.emptyList();
    private String prefixResponse = prefixTag + "project.UNKNOWN." + prefixTag + "test.UNKNOWN." + SystemEnv.STATSD_RESPONSE_KEY.getValue();
    private String prefixStatsdLoaderKey = getPrefixStatsdLoader(null);
    private String prefixStatsdTargetsKey = getPrefixStatsdTargets(null);

    @Autowired
    public MonitorService(final StatsdService statsdService) {
        this.statsdClient = statsdService.client();
    }

    public void monitoring(final Test test, int delta) {
        synchronized (lock) {
            if (!this.test.compareAndSet(null, test)) {
                throw new IllegalStateException("Already monitoring other test");
            }
            this.delta = delta;
            prefixStatsdLoaderKey = getPrefixStatsdLoader(test);
            prefixStatsdTargetsKey = getPrefixStatsdTargets(test);
            extractMonitTargets(test);
        }
    }

    private String getPrefixStatsdTargets(final Test test) {
        String testName = test != null ? test.getName() : UNKNOWN;
        String testProject = test != null ? test.getProject() : UNKNOWN;
        return String.format("%sproject.%s.%stest.%s.%s%s.", prefixTag, testProject, prefixTag, testName, prefixTag, SystemEnv.STATSD_TARGET_KEY.getValue());
    }

    private String getPrefixStatsdLoader(final Test test) {
        String testName = test != null ? test.getName() : UNKNOWN;
        String testProject = test != null ? test.getProject() : UNKNOWN;
        return String.format("%sproject.%s.%stest.%s.%s%s.%s.", prefixTag, testProject, prefixTag, testName, prefixTag, SystemEnv.STATSD_LOADER_KEY.getValue(), hostnameFormated);
    }

    private String getStatsdPrefixResponse(final Test test) {
        String testName = test != null ? test.getName() : UNKNOWN;
        String testProject = test != null ? test.getProject() : UNKNOWN;
        return String.format("%sproject.%s.%stest.%s.%s.", prefixTag, testProject, prefixTag, testName, SystemEnv.STATSD_RESPONSE_KEY.getValue());
    }

    private void extractMonitTargets(final Test test) {
        this.prefixResponse = getStatsdPrefixResponse(test);
        final Map<String, Object> properties = test.getProperties();
        String monitTargets = (String) properties.get("monitTargets");
        if (monitTargets != null) {
            targets = Arrays.stream(monitTargets.split(",")).map(String::trim).map(URI::create).map(mapUriToMetricsCollector()).collect(Collectors.toList());
        } else {
            targets = Collections.emptyList();
        }
    }

    private Function<URI, MetricsCollector> mapUriToMetricsCollector() {
        return uri -> {
            String uriScheme = uri.getScheme();
            if (uriScheme != null) {
                try {
                    return MetricsCollectorByScheme.valueOf(uriScheme.toUpperCase()).collect(uri);
                } catch (Exception e) {
                    LOGGER.warn("Monitoring scheme problem (" + uri.getScheme() + "). Using ZeroCollector because " + e.getMessage());
                    return new MetricsCollectorByScheme.ZeroCollector().setUri(uri);
                }
            }
            return new MetricsCollectorByScheme.ZeroCollector().setUri(uri);
        };
    }

    public void reset() {
        synchronized (lock) {
            this.test.set(null);
            this.targets = Collections.emptyList();
            this.prefixResponse = getStatsdPrefixResponse(null);
            this.prefixStatsdLoaderKey = getPrefixStatsdLoader(null);
            this.prefixStatsdTargetsKey = getPrefixStatsdTargets(null);
            delta = 0;
        }
    }

    public void completed(final Response response, long start) {
        try {
            int statusCode = response.getStatusCode();
            int bodySize = response.getResponseBodyAsBytes().length;
            statsdClient.recordExecutionTime(prefixResponse + prefixTag + "status." + statusCode, System.currentTimeMillis() - start);
            statsdClient.recordExecutionTime(prefixResponse + "size", bodySize);
        } catch (Exception e) {
            fail(e, start);
        }
    }

    public void fail(final Throwable t, long start) {
        if (!((t instanceof TooManyConnectionsException) || (t instanceof TooManyConnectionsPerHostException) || t.getMessage().contains("executor not accepting a task"))) {
            String messageException = t.getMessage().replaceAll("[ .:/]", "_").replaceAll(".*Exception__", "");
            statsdClient.recordExecutionTime(prefixResponse + "status." + messageException, System.currentTimeMillis() - start);
            statsdClient.recordExecutionTime(prefixResponse + "size", 0);
            LOGGER.error(t);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void sendMetrics() throws IOException {
        synchronized (lock) {
            if (test.get() != null) {
                int tcpConn = SystemInfo.totalSocketsTcpEstablished();
                statsdClient.gauge(prefixStatsdLoaderKey + "conns", Math.max(0, tcpConn - delta));
                statsdClient.gauge(prefixStatsdLoaderKey + "cpu", 100 * SystemInfo.cpuLoad());
                statsdClient.gauge(prefixStatsdLoaderKey + "memFree", SystemInfo.memFree());

                targets.forEach(target -> {
                    String prefixStatsd = prefixStatsdTargetsKey + target.getKey() + ".";
                    int targetConns = target.getConns();
                    double targetMemFree = target.getMemFree();
                    int targetCpuUsed = target.getCpuUsed();
                    float targetLoad1m = target.getLoad1m();
                    float targetLoad5m = target.getLoad5m();
                    float targetLoad15m = target.getLoad15m();

                    statsdClient.gauge(prefixStatsd + "conns", targetConns);
                    statsdClient.gauge(prefixStatsd + "cpu", targetCpuUsed);
                    statsdClient.gauge(prefixStatsd + "memFree", targetMemFree);
                    statsdClient.gauge(prefixStatsd + "load1m", targetLoad1m);
                    statsdClient.gauge(prefixStatsd + "load5m", targetLoad5m);
                    statsdClient.gauge(prefixStatsd + "load15m", targetLoad15m);
                });
            }
        }
    }
}
