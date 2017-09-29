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

import com.globocom.grou.groot.statsd.StatsdService;
import io.galeb.statsd.StatsDClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.asynchttpclient.exception.TooManyConnectionsException;
import org.asynchttpclient.exception.TooManyConnectionsPerHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestExecutorService {

    private final Log log = LogFactory.getLog(this.getClass());

    private final StatsDClient statsdClient;

    @Autowired
    public RequestExecutorService(StatsdService statsdService) {
        this.statsdClient = statsdService.client();
    }

    public void execute(final AsyncHttpClient asyncHttpClient, final ParameterizedRequest request) {

        String prefixStatsdKey = request.getTestProject() + "." + request.getTestName();
        final long start = System.currentTimeMillis();
        asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {

            @Override
            public Response onCompleted(Response response) throws Exception {
                int statusCode = response.getStatusCode();
                int bodySize = response.getResponseBodyAsBytes().length;
                statsdClient.recordExecutionTime(prefixStatsdKey + ".status." + statusCode, System.currentTimeMillis() - start);
                statsdClient.count(prefixStatsdKey + ".responseSize", bodySize);
                return response;
            }

            @Override
            public void onThrowable(Throwable t) {
                if (!((t instanceof TooManyConnectionsException) || (t instanceof TooManyConnectionsPerHostException) || t.getMessage().contains("executor not accepting a task"))) {
                    String messageException = t.getMessage().replaceAll("[ .:/]", "_").replaceAll(".*Exception__", "");
                    statsdClient.recordExecutionTime(prefixStatsdKey + "." + messageException, System.currentTimeMillis() - start);
                    log.error(t);
                }
            }
        });
    }
}
