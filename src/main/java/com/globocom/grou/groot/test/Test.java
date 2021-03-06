/*
 * Copyright (c) 2017-2018 Globo.com
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

package com.globocom.grou.groot.test;

import com.globocom.grou.groot.test.properties.BaseProperty;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class Test implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        SCHEDULED,
        ENQUEUED,
        RUNNING,
        OK,
        ERROR,
        ABORTED
    }

    private String id;

    private String createdBy;

    private Date createdDate;

    private String lastModifiedBy;

    private Date lastModifiedDate;

    private String name;

    private String project;

    private Set<Loader> loaders = new HashSet<>();

    private BaseProperty properties = new BaseProperty();

    private Set<String> tags = new HashSet<>();

    private Status status = Status.SCHEDULED;

    private HashMap<String, Double> result = null;

    private HashSet<String> notify = new HashSet<>();

    private String dashboard;

    private int durationTimeMillis;

    public String getId() {
        return id;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Date getLastModifiedDate() {
        return lastModifiedDate;
    }

    public String getName() {
        return name;
    }

    public String getProject() {
        return project;
    }

    public BaseProperty getProperties() {
        return properties;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        if (tags != null) {
            this.tags = tags;
        }
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Set<Loader> getLoaders() {
        return loaders;
    }

    public void setLoaders(Set<Loader> loaders) {
        if (loaders != null) {
            this.loaders = loaders;
        }
    }

    public HashMap<String, Double> getResult() {
        return result;
    }

    public void setResult(HashMap<String, Double> result) {
        this.result = result;
    }

    public Set<String> getNotify() {
        return notify;
    }

    public void setNotify(Set<String> notify) {
        if (notify != null) this.notify = new HashSet<>(notify);
    }

    public String getDashboard() {
        return dashboard;
    }

    public int getDurationTimeMillis() {
        return durationTimeMillis;
    }

    public void setDurationTimeMillis(int durationTimeMillis) {
        this.durationTimeMillis = durationTimeMillis;
    }
}
