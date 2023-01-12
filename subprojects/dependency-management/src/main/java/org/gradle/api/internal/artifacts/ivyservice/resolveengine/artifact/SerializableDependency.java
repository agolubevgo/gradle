/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.Dependency;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;

public class SerializableDependency implements Dependency, Serializable {
    String name;
    String group;
    String version;
    String reason;

    public SerializableDependency(String name, String group, String version, String reason) {
        this.name = name;
        this.group = group;
        this.version = version;
        this.reason = reason;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        return Objects.equals(dependency.getName(),name) &&
            Objects.equals(dependency.getGroup(),group) &&
            Objects.equals(dependency.getVersion(),version);
    }

    @Override
    public Dependency copy() {
        return new SerializableDependency(name, group, version,reason);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public void because(@Nullable String reason) {
        this.reason = reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
