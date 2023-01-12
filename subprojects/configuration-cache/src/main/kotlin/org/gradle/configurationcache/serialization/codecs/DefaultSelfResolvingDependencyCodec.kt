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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull

class DefaultSelfResolvingDependencyCodec: Codec<DefaultSelfResolvingDependency> {
    override suspend fun WriteContext.encode(value: DefaultSelfResolvingDependency) {
        write(value.targetComponentId)
        write(value.files)
    }

    override suspend fun ReadContext.decode(): DefaultSelfResolvingDependency {
        val componentId = read() as ComponentIdentifier?
        val artifacts = readNonNull<FileCollectionInternal>()
        return DefaultSelfResolvingDependency (componentId, artifacts)
    }
}
