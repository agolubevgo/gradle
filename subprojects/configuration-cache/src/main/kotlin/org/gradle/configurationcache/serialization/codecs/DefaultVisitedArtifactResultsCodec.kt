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

import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolutionStrategy.SortOrder
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultVisitedArtifactResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.specs.Spec
import org.gradle.configurationcache.serialization.*
import org.gradle.configurationcache.serialization.readFile
import org.gradle.configurationcache.serialization.writeFile
import org.gradle.internal.Describables
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier

class DefaultVisitedArtifactResultsCodec: Codec<DefaultVisitedArtifactResults> {
    override suspend fun WriteContext.encode(value: DefaultVisitedArtifactResults) {
        writeEnum(value.sortOrder)
        writeCollection(value.artifactsById)
    }

    override suspend fun ReadContext.decode(): DefaultVisitedArtifactResults {
        val sortOrder = readEnum<SortOrder>()
        val mutableList = mutableListOf<ArtifactSet>()
        readCollection {
            mutableList.add(readNonNull())
        }
        return DefaultVisitedArtifactResults(sortOrder,mutableList)
    }
}
