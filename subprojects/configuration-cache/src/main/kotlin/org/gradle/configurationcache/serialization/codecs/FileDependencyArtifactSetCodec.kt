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

import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.configurationcache.serialization.*
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.reflect.Instantiator

class FileDependencyArtifactSetCodec(private var calculatedValueContainerFactory: CalculatedValueContainerFactory,
                                     private val instantiator: Instantiator,
                                     private val attributesFactory: ImmutableAttributesFactory):
    Codec<FileDependencyArtifactSet> {

    override suspend fun WriteContext.encode(value: FileDependencyArtifactSet) {
        write(value.fileDependency)
        encodePreservingSharedIdentityOf(value.artifactTypeRegistry) {
            val mappings = value.artifactTypeRegistry.create()!!
            writeCollection(mappings) {
                writeString(it.name)
                write(it.attributes)
            }
        }
    }

    override suspend fun ReadContext.decode(): FileDependencyArtifactSet {
        val metadata = readNonNull<LocalFileDependencyMetadata>()
        val artifactTypeRegistry = decodePreservingSharedIdentity {
            val registry = DefaultArtifactTypeRegistry(instantiator, attributesFactory, CollectionCallbackActionDecorator.NOOP, EmptyVariantTransformRegistry)
            val mappings = registry.create()!!
            readCollection {
                val name = readString()
                val attributes = readNonNull<AttributeContainer>()
                val mapping = mappings.create(name).attributes
                @Suppress("UNCHECKED_CAST")
                for (attribute in attributes.keySet() as Set<Attribute<Any>>) {
                    mapping.attribute(attribute, attributes.getAttribute(attribute) as Any)
                }
            }
            registry
        }
        return FileDependencyArtifactSet(metadata, artifactTypeRegistry, calculatedValueContainerFactory);
    }
}
