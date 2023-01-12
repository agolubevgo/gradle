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

package org.gradle.configurationcache

import org.gradle.api.logging.LogLevel
import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.BlockAddressSerializer
import org.gradle.configurationcache.cacheentry.EntryDetails
import org.gradle.configurationcache.cacheentry.ModelKey
import org.gradle.configurationcache.extensions.useToRun
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.*
import org.gradle.configurationcache.serialization.beans.BeanStateReaderLookup
import org.gradle.configurationcache.serialization.beans.BeanStateWriterLookup
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import java.io.File
import java.io.InputStream
import java.io.OutputStream


@ServiceScope(Scopes.Gradle::class)
class DependencyCacheIO internal constructor(
    private val host: DefaultConfigurationCache.Host,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val beanStateWriterLookup: BeanStateWriterLookup,
    private val beanStateReaderLookup: BeanStateReaderLookup,

    ) {
    private
    val codecs = codecs()

    fun writerContextFor(outputStream: OutputStream, profile: String): Pair<DefaultWriteContext, Codecs> =
        KryoBackedEncoder(outputStream).let { encoder ->
            writeContextFor(
                encoder,
                codecs
            ) to codecs
        }

    private
    fun readerContextFor(
        inputStream: InputStream,
    ) = readerContextFor(KryoBackedDecoder(inputStream))

    fun <R> withReadContextFor(
        inputStream: InputStream,
        readOperation: suspend DefaultReadContext.(Codecs) -> R
    ): R =
        readerContextFor(inputStream).let { (context, codecs) ->
            context.use {
                context.run {
                    initClassLoader(javaClass.classLoader)
                    runReadOperation {
                        readOperation(codecs)
                    }.also {
                        finish()
                    }
                }
            }
        }

    internal
    fun readerContextFor(
        decoder: Decoder,
    ) =
        readContextFor(decoder, codecs).apply {
            initClassLoader(javaClass.classLoader)
        } to codecs

    private
    fun writeContextFor(
        encoder: Encoder,
        codecs: Codecs
    ) = DefaultWriteContext(
        codecs.userTypesCodec(),
        encoder,
        scopeRegistryListener,
        beanStateWriterLookup,
        logger,
        null,
        null
    )

    private
    fun readContextFor(
        decoder: Decoder,
        codecs: Codecs
    ) = DefaultReadContext(
        codecs.userTypesCodec(),
        decoder,
        beanStateReaderLookup,
        logger,
        null
    )

    private
    fun codecs(): Codecs =
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            artifactSetConverter = service(),
            fileLookup = service(),
            propertyFactory = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            listenerManager = service(),
            taskNodeFactory = service(),
            ordinalGroupFactory = service(),
            inputFingerprinter = service(),
            buildOperationExecutor = service(),
            classLoaderHierarchyHasher = service(),
            isolatableFactory = service(),
            managedFactoryRegistry = service(),
            parameterScheme = service(),
            actionScheme = service(),
            attributesFactory = service(),
            valueSourceProviderFactory = service(),
            calculatedValueContainerFactory = service(),
            patternSetFactory = factory(),
            fileOperations = service(),
            fileFactory = service(),
            includedTaskGraph = service(),
            buildStateRegistry = service(),
            documentationRegistry = service(),
            javaSerializationEncodingLookup = service(),
            crossBuildInMemoryCacheFactory = service()
        )

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)
}
