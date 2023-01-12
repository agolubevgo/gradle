/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.configurationcache.serialization.*
import org.gradle.configurationcache.serialization.codecs.EmptyVariantTransformRegistry
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.reflect.Instantiator

class DefaultArtifactSetCodec(private val instantiator: Instantiator,
                              private val attributesFactory: ImmutableAttributesFactory,
                              private val fileCollectionFactory: FileCollectionFactory,
                              private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
                              private val cacheFactory: CrossBuildInMemoryCacheFactory
):
    Codec<DefaultArtifactSet> {

    override suspend fun WriteContext.encode(value: DefaultArtifactSet) {
        write(value.componentIdentifier)
        write(value.overriddenAttributes)
        //schema ???
        writeCollection(value.allVariants)
        writeCollection(value.variants)
    }

    override suspend fun ReadContext.decode(): DefaultArtifactSet {
        val identifier = readNonNull<ComponentIdentifier>()
        val attributes = readNonNull<ImmutableAttributes>()
        val variants = mutableSetOf<ResolvedVariant>()
            readCollection { variants.add(readNonNull()) }
        val legacyVariants = mutableSetOf<ResolvedVariant>()
            readCollection { legacyVariants.add(readNonNull()) }

        val nameInstantiator  = NamedObjectInstantiator(cacheFactory)
        val schema = PreferJavaRuntimeVariant(nameInstantiator);
        return DefaultArtifactSet(identifier,
            schema,
            attributes,
            SimpleComponentArtifactResolveVariantState(variants),
            legacyVariants);
    }

    class SimpleComponentArtifactResolveVariantState(var variants: Set<ResolvedVariant>):
        ComponentArtifactResolveVariantState {
        override fun getAllVariants(): MutableSet<ResolvedVariant> =
            variants.toMutableSet()

    }
}
