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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults
import org.gradle.configurationcache.extensions.useToRun
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.internal.UniversalSerializator
import org.gradle.internal.service.ServiceCreationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DefaultUniversalSerializator(private val host: DefaultConfigurationCache.Host,
                                   private val gradle: GradleInternal): UniversalSerializator {
    private var cacheIO:DependencyCacheIO? = null
    override fun isReady():Boolean {
        if(cacheIO!=null) return true
        else{
            try {
                cacheIO = gradle.services.get(DependencyCacheIO::class.java)
            }catch (ex : ServiceCreationException){}
        }
        return cacheIO != null
    }
    override fun serializeResult(prefix: String, suffix: String, toSerialize: Any): File {
        val f = File.createTempFile(prefix + suffix, ".bin")
        val outputStream = FileOutputStream(f)
                val (context, codecs) =
                    cacheIO!!.writerContextFor(outputStream, "na")
                //context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec())
                context.useToRun {
                        runWriteOperation {
                            withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
                                write(toSerialize)
                            }
                        }
                }

        return f;
    }

    override fun readArtifacts(file: File?): Any{
        val inputStream = FileInputStream(file)
        var result:VisitedArtifactsResults? = null;
        cacheIO!!.withReadContextFor(inputStream) { codecs ->
            withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
                result =  readNonNull<VisitedArtifactsResults>()
            }
        }
        return result!!
    } //VisitedArtifactsResults

    override fun readDependency(file: File?): Any? //VisitedFileDependencyResults
     { val inputStream = FileInputStream(file)
    var result: VisitedFileDependencyResults? = null;
    cacheIO!!.withReadContextFor(inputStream) { codecs ->
        withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
            result =  readNonNull<VisitedFileDependencyResults>()
        }
    }
    return result!!
}

}
