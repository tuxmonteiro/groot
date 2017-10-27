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

package com.globocom.grou.groot.httpclient;

import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.entities.properties.GrootProperties;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.monit.SystemInfo;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.asynchttpclient.handler.ProgressAsyncHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

@Service
public class RequestExecutorService {

    private static final Log LOGGER = LogFactory.getLog(RequestExecutorService.class);

    private final MonitorService monitorService;

    @Autowired
    public RequestExecutorService(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    public void execute(final AsyncHttpClient asyncHttpClient, final Request request) {
        asyncHttpClient.executeRequest(request, new NoBodyCopyAsyncHandler());
    }

    public AsyncHttpClient newClient(final Map<String, Object> testProperties, int durationTimeMillis) throws IllegalArgumentException {
        Object parallelLoadersObj = testProperties.get(GrootProperties.PARALLEL_LOADERS);
        Object connectTimeoutObj = testProperties.get(GrootProperties.CONNECTION_TIMEOUT);
        Object keepAliveObj = testProperties.get(GrootProperties.KEEP_ALIVE);
        Object followRedirectObj = testProperties.get(GrootProperties.FOLLOW_REDIRECT);

        int parallelLoaders = parallelLoadersObj != null && parallelLoadersObj instanceof Integer ? (int) parallelLoadersObj : 1;
        int numConn = (Integer) testProperties.get(GrootProperties.NUM_CONN) / Math.max(1, parallelLoaders);
        int connectTimeout = connectTimeoutObj != null && connectTimeoutObj instanceof Integer ? (int) connectTimeoutObj : 2000;
        boolean keepAlive = keepAliveObj == null || !(keepAliveObj instanceof Boolean) || (boolean) keepAliveObj;
        boolean followRedirect = (followRedirectObj != null && followRedirectObj instanceof Boolean) && (boolean) followRedirectObj;

        DefaultAsyncHttpClientConfig.Builder config = config()
                .setFollowRedirect(followRedirect)
                .setSoReuseAddress(true)
                .setKeepAlive(keepAlive)
                .setConnectTimeout(connectTimeout)
                .setPooledConnectionIdleTimeout(durationTimeMillis)
                .setConnectionTtl(durationTimeMillis)
                .setMaxConnectionsPerHost(numConn)
                .setMaxConnections(numConn)
                .setRequestTimeout(durationTimeMillis)
                .setReadTimeout(durationTimeMillis)
                .setUseInsecureTrustManager(true)
                .setUserAgent(Application.GROOT_USERAGENT);

        if (SystemInfo.isLinux()) {
            config.setUseNativeTransport(true);
        }

        return asyncHttpClient(config);
    }

    private class NoBodyCopyAsyncHandler implements AsyncHandler<Response>, ProgressAsyncHandler<Response> {

        private final long start = System.currentTimeMillis();

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
            monitorService.sendSize(content.length());
            return State.CONTINUE;
        }

        @Override
        public void onThrowable(Throwable t) {
            monitorService.fail(t, start);
        }

        @Override
        public State onStatusReceived(HttpResponseStatus status) throws Exception {
            monitorService.sendStatus(String.valueOf(status.getStatusCode()), start);
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpHeaders headers) throws Exception {
            final AtomicInteger headersSize = new AtomicInteger(0);
            // HINT: +2 bytes because has ": " between the key/value
            headers.iteratorCharSequence().forEachRemaining(e ->
                    headersSize.addAndGet(e.getKey().length() + e.getValue().length() + 2));
            monitorService.sendSize(headersSize.get());
            return State.CONTINUE;
        }

        @Override
        public State onTrailingHeadersReceived(HttpHeaders headers) throws Exception {
            return State.CONTINUE;
        }

        @Override
        public Response onCompleted() throws Exception {
            return onCompleted(null);
        }

        @SuppressWarnings("SameParameterValue")
        Response onCompleted(Response response) throws Exception {
            monitorService.sendResponseTime(start);
            return response;
        }

        @Override
        public State onHeadersWritten() {
            return State.CONTINUE;
        }

        @Override
        public State onContentWritten() {
            return State.CONTINUE;
        }

        @Override
        public State onContentWriteProgress(long amount, long current, long total) {
            return State.CONTINUE;
        }

    }

}
