/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesOnlyVisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SerializableDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.FailOnVersionConflictArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolutionFailureCollector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.SerializableGraphResultFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.FileDependencyCollectingGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.DefaultBinaryStore;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.BuildCacheController;

import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.UniversalSerializator;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.locking.DependencyLockingArtifactVisitor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.serialize.kryo.SimpleKryoUtils;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.*;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DefaultConfigurationResolver implements ConfigurationResolver {
    private static final Spec<DependencyMetadata> IS_LOCAL_EDGE = element -> element instanceof DslOriginDependencyMetadata && ((DslOriginDependencyMetadata) element).getSource() instanceof ProjectDependency;
    private final ArtifactDependencyResolver resolver;
    private final RepositoriesSupplier repositoriesSupplier;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;
    private final AttributesSchemaInternal attributesSchema;
    private final ArtifactTransforms artifactTransforms;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final BuildIdentifier currentBuild;
    private final AttributeDesugaring attributeDesugaring;
    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final ProjectDependencyResolver projectDependencyResolver;
    private final boolean syncMode;
    private  BuildCacheController cacheController;
    private final ServiceRegistry services;
    private UniversalSerializator serializer;
    public DefaultConfigurationResolver(
        ArtifactDependencyResolver resolver,
        RepositoriesSupplier repositoriesSupplier,
        GlobalDependencyResolutionRules metadataHandler,
        ResolutionResultsStoreFactory storeFactory,
        boolean buildProjectDependencies,
        AttributesSchemaInternal attributesSchema,
        ArtifactTransforms artifactTransforms,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        BuildOperationExecutor buildOperationExecutor,
        ArtifactTypeRegistry artifactTypeRegistry,
        ComponentSelectorConverter componentSelectorConverter,
        AttributeContainerSerializer attributeContainerSerializer,
        BuildIdentifier currentBuild, AttributeDesugaring attributeDesugaring,
        DependencyVerificationOverride dependencyVerificationOverride,
        ProjectDependencyResolver projectDependencyResolver,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        WorkerLeaseService workerLeaseService,
        ServiceRegistry services,
        boolean syncMode
    ) {
        this.resolver = resolver;
        this.repositoriesSupplier = repositoriesSupplier;
        this.metadataHandler = metadataHandler;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = buildProjectDependencies;
        this.attributesSchema = attributesSchema;
        this.artifactTransforms = artifactTransforms;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.currentBuild = currentBuild;
        this.attributeDesugaring = attributeDesugaring;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.projectDependencyResolver = projectDependencyResolver;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.workerLeaseService = workerLeaseService;
        this.syncMode = syncMode;
        this.services = services;
    }

    @Override
    public void resolveBuildDependencies(ConfigurationInternal configuration, ResolverResults result) {
        ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        InMemoryResolutionResultBuilder resolutionResultBuilder = new InMemoryResolutionResultBuilder();
        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild);
        CompositeDependencyGraphVisitor graphVisitor = new CompositeDependencyGraphVisitor(failureCollector, resolutionResultBuilder, localComponentsVisitor);
        DefaultResolvedArtifactsBuilder artifactsVisitor = new DefaultResolvedArtifactsBuilder(buildProjectDependencies, resolutionStrategy.getSortOrder());
        resolver.resolve(configuration, ImmutableList.of(), metadataHandler, IS_LOCAL_EDGE, graphVisitor, artifactsVisitor, attributesSchema, artifactTypeRegistry, projectDependencyResolver, false);
        result.graphResolved(resolutionResultBuilder.getResolutionResult(), localComponentsVisitor, new BuildDependenciesOnlyVisitedArtifactSet(failureCollector.complete(Collections.emptySet()), artifactsVisitor.complete(), artifactTransforms, configuration.getDependenciesResolver()));
    }


    private HashCode appendSuffix(HashCode code, String suffix){
        Hasher newHasher = Hashing.newHasher();
        newHasher.putHash(code);
        newHasher.putString(suffix);
        return newHasher.hash();
    }
    private HashCode generateUniqueId(ConfigurationInternal configuration) {
        // TODO serialize resolution strategy settings
        Hasher hasher = Hashing.newHasher();
        DependencySet dependencies = configuration.getAllDependencies();
        hasher.putString(currentBuild.getName());
        hasher.putString(configuration.getName());
        StringBuffer sb = new StringBuffer();
        for (Dependency dependency : dependencies) {
            sb.append(dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion() + ",");
        }
        hasher.putString(sb.toString());
        return hasher.hash();
    }


    @Override
    public void resolveGraph(ConfigurationInternal configuration, ResolverResults results) {
        if(cacheController == null) {
               cacheController = services.get(BuildCacheController.class);
        }
        if(serializer ==null){
            serializer = services.get(UniversalSerializator.class);
        }
        boolean cachIsReady =  serializer.isReady();
        List<ResolutionAwareRepository> resolutionAwareRepositories = getRepositories();
        StoreSet stores = storeFactory.createStoreSet();

        Optional<HashCode> cacheId = (syncMode) ? Optional.of(generateUniqueId(configuration)) : Optional.empty();

        DefaultBinaryStore oldModelStore = stores.nextBinaryStore(Optional.empty());
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();

        DefaultBinaryStore newModelStore = stores.nextBinaryStore(Optional.empty());
        Store<ResolvedComponentResult> newModelCache = stores.newModelCache();

        Optional<BuildCacheLoadResult> newDepResult = Optional.empty();
        if (cacheId.isPresent()) {
            try {

                File artifactFile = File.createTempFile(cacheId.get().toString()+"artifact", ".bin");
                File graphFile = File.createTempFile(cacheId.get().toString()+"graph", ".bin");
                File dependencyFile = File.createTempFile(cacheId.get().toString()+"dependency", ".bin");

                CacheableArtifactDependencies dependencies = new CacheableArtifactDependencies(cacheId.get().toString(),
                    newModelStore, oldModelStore,
                    graphFile, artifactFile,dependencyFile);
                newDepResult = cacheController.load(
                    new DependencyBuildCacheKey(cacheId.get().toString()),
                    dependencies
                );
            }catch(IOException ex){
                //ignore for now
            }
        }

        if (!newDepResult.isPresent()) {
            TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache, moduleIdentifierFactory, buildOperationExecutor);
            DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
            ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

            StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache, moduleIdentifierFactory, attributeContainerSerializer, attributeDesugaring, componentSelectionDescriptorFactory, configuration.getReturnAllVariants());

            ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild);

            //if(!syncMode) {
            ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
            DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies, resolutionStrategy.getSortOrder());
            FileDependencyCollectingGraphVisitor fileDependencyVisitor = new FileDependencyCollectingGraphVisitor();
            ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
            DependencyGraphVisitor graphVisitor = new CompositeDependencyGraphVisitor(newModelBuilder, localComponentsVisitor, failureCollector);

            ImmutableList.Builder<DependencyArtifactsVisitor> visitors = new ImmutableList.Builder<>();
            visitors.add(oldModelVisitor);
            visitors.add(fileDependencyVisitor);
            visitors.add(artifactsBuilder);
            if (resolutionStrategy.getConflictResolution() == ConflictResolution.strict) {
                ProjectComponentIdentifier projectId = configuration.getModule().getProjectId();
                // projectId is null for DefaultModule used in settings
                String projectPath = projectId != null
                    ? projectId.getProjectPath()
                    : "";
                visitors.add(new FailOnVersionConflictArtifactsVisitor(projectPath, configuration.getName()));
            }
            DependencyLockingArtifactVisitor lockingVisitor = null;
            if (resolutionStrategy.isDependencyLockingEnabled()) {
                lockingVisitor = new DependencyLockingArtifactVisitor(configuration.getName(), resolutionStrategy.getDependencyLockingProvider());
                visitors.add(lockingVisitor);
            } else {
                resolutionStrategy.confirmUnlockedConfigurationResolved(configuration.getName());
            }
            ImmutableList<DependencyArtifactsVisitor> allVisitors = visitors.build();
            CompositeDependencyArtifactsVisitor artifactsVisitor = new CompositeDependencyArtifactsVisitor(allVisitors);

            resolver.resolve(configuration, resolutionAwareRepositories, metadataHandler, Specs.satisfyAll(), graphVisitor, artifactsVisitor, attributesSchema, artifactTypeRegistry, projectDependencyResolver, true);
            //}
            //
            //TODO

            VisitedArtifactsResults artifactsResults = artifactsBuilder.complete(); //store
            VisitedFileDependencyResults fileDependencyResults = fileDependencyVisitor.complete(); //store
            ResolvedGraphResults graphResults = oldModelBuilder.complete(); //store

            // Append to failures for locking and fail on version conflict
            Set<UnresolvedDependency> extraFailures = lockingVisitor == null
                ? Collections.emptySet()
                : lockingVisitor.collectLockingFailures(); //skip for now
            Set<UnresolvedDependency> failures = failureCollector.complete(extraFailures);
            ResolutionResult result = newModelBuilder.complete(extraFailures);
            results.graphResolved(result, localComponentsVisitor, new BuildDependenciesOnlyVisitedArtifactSet(failures, artifactsResults, artifactTransforms, configuration.getDependenciesResolver()));

            results.retainState(new ArtifactResolveState(graphResults, artifactsResults, fileDependencyResults, failures, oldTransientModelBuilder));
            if (!results.hasError() && failures.isEmpty()) {
                artifactsVisitor.complete();
            }

            if (syncMode && cachIsReady) {
                try {
                    Map<Long, SerializableDependency> newGraphResult = SerializableGraphResultFactory.createSerializableDependency(graphResults);
                    RegularFileSnapshot artifactSnapshot = serializeResult(appendSuffix(cacheId.get(), "artifacts"), artifactsResults);
                    RegularFileSnapshot dependencySnapshot = serializeResult(appendSuffix(cacheId.get(), "dependency"), fileDependencyResults);

                    HashCode graphHash = appendSuffix(cacheId.get(), "graph");
                    File storeFile = File.createTempFile(graphHash.toString(), ".bin");
                    RegularFileSnapshot graphSnapshot = serializeToFile(graphHash, newGraphResult, storeFile);

                    // DependencyResultContainer container = new DependencyResultContainer(artifactsResults, fileDependencyResults, newGraphResult);

                    CacheableArtifactDependencies cachable = new CacheableArtifactDependencies(cacheId.get().toString(),
                        newModelStore, oldModelStore,
                        new File(artifactSnapshot.getAbsolutePath()),
                        new File(dependencySnapshot.getAbsolutePath()),
                        storeFile);
                    HashMap<String, FileSystemSnapshot> map = new HashMap<>();
                    map.put("old", oldModelStore.makeSnapshot(appendSuffix(cacheId.get(), "old")));
                    map.put("new", newModelStore.makeSnapshot(appendSuffix(cacheId.get(), "new")));
                    map.put("artifact", artifactSnapshot);
                    map.put("dependency", dependencySnapshot);
                    map.put("graph", graphSnapshot);
                    cacheController.store(new DependencyBuildCacheKey(cacheId.get().toString()), cachable, map, Duration.ZERO);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }
        } else {
            //deserialize
            try {
                oldModelStore.loadedFromCache();
                newModelStore.loadedFromCache();
                ResolvedGraphResults graphResults = new DefaultResolvedGraphResults(deserializeSnapshotFile(newDepResult.get().getResultingSnapshots().get("graph")));
                VisitedArtifactsResults artifactsResults =  deserializeArtifact(newDepResult.get().getResultingSnapshots().get("artifact"));
                VisitedFileDependencyResults fileDependencyResults = deserializeDependency(newDepResult.get().getResultingSnapshots().get("dependency"));
                Set<UnresolvedDependency> extraFailures = Collections.emptySet();
                ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
                Set<UnresolvedDependency> failures = failureCollector.complete(extraFailures);
                ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild);

                TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache, moduleIdentifierFactory, buildOperationExecutor);
                StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache, moduleIdentifierFactory, attributeContainerSerializer, attributeDesugaring, componentSelectionDescriptorFactory, configuration.getReturnAllVariants());

                ResolutionResult result = newModelBuilder.complete(extraFailures);
                results.graphResolved(result, localComponentsVisitor, new BuildDependenciesOnlyVisitedArtifactSet(failures, artifactsResults, artifactTransforms, configuration.getDependenciesResolver()));

                results.retainState(new ArtifactResolveState(graphResults, artifactsResults, fileDependencyResults, failures, oldTransientModelBuilder));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }


    public RegularFileSnapshot serializeResult(HashCode hash, Object toSerialize)
        throws IOException, ExecutionException, InterruptedException {

        File f = serializer.serializeResult(hash.toString(),"",toSerialize);
        /*File f = File.createTempFile(prefix + suffix, ".bin");
        try(FileOutputStream fileOutputStream = new FileOutputStream(f);
            BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream)) {

            Pair<DefaultWriteContext, Codecs> contextAndCodecs = cacheIO.writerContextForJava(outputStream, "na");
            CompletableFuture<Unit> result =
                contextAndCodecs.left.writeAync(toSerialize);
            result.get(); //TODO now block

        }*/
        FileMetadata metadata = DefaultFileMetadata.file(f.lastModified(), f.length(), FileMetadata.AccessType.DIRECT);
        return new RegularFileSnapshot(f.getAbsolutePath(),f.getName(), hash, metadata);
    }

    private VisitedFileDependencyResults deserializeDependency(FileSystemSnapshot snapshot) throws IOException {
        final List<VisitedFileDependencyResults> result = new ArrayList<>();
        snapshot.accept(new FileSystemSnapshotHierarchyVisitor(){

            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                File file = new File(snapshot.getAbsolutePath());
                    result.add((VisitedFileDependencyResults)serializer.readDependency(file));

                return SnapshotVisitResult.CONTINUE;
            }}
        );
        return result.get(0);
    }

    private VisitedArtifactsResults deserializeArtifact(FileSystemSnapshot snapshot) throws IOException {
        final List<VisitedArtifactsResults> result = new ArrayList<>();
        snapshot.accept(new FileSystemSnapshotHierarchyVisitor(){

            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                File file = new File(snapshot.getAbsolutePath());
                result.add((VisitedArtifactsResults)serializer.readArtifacts(file));
                return SnapshotVisitResult.CONTINUE;
            }}
        );
        return result.get(0);
    }

    private Map<Long, Dependency> deserializeSnapshotFile(FileSystemSnapshot snapshot) throws IOException {
        final Map<Long, Dependency> result = new HashMap<>();
        snapshot.accept(new FileSystemSnapshotHierarchyVisitor(){

            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                File file = new File(snapshot.getAbsolutePath());
                try(FileInputStream fileStream = new FileInputStream(file);
                    ObjectInputStream stream =new ObjectInputStream(fileStream)){
                    @SuppressWarnings("unchecked")
                        Map<Long, SerializableDependency> res = (Map<Long, SerializableDependency>) stream.readObject();
                    result.putAll( res);
            } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return SnapshotVisitResult.CONTINUE;
            }}
        );
        return result;
    }

    public RegularFileSnapshot serializeToFile(
        HashCode hash,
        Object container,
        File tmpFile
    ) throws IOException {
        try(FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
            BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream) ) {
            objectOutputStream.writeObject(container);
        }
        FileMetadata metadata = DefaultFileMetadata.file(tmpFile.lastModified(), tmpFile.length(), FileMetadata.AccessType.DIRECT);
        return new RegularFileSnapshot(tmpFile.getAbsolutePath(),tmpFile.getName(), hash, metadata);
    }



    @Override
    public List<ResolutionAwareRepository> getRepositories() {
        return Cast.uncheckedCast(repositoriesSupplier.get());
    }

    @Override
    public void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) {
        ArtifactResolveState resolveState = (ArtifactResolveState) results.getArtifactResolveState();
        ResolvedGraphResults graphResults = resolveState.graphResults;
        VisitedArtifactsResults artifactResults = resolveState.artifactsResults;
        TransientConfigurationResultsBuilder transientConfigurationResultsBuilder = resolveState.transientConfigurationResultsBuilder;

        TransientConfigurationResultsLoader transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(transientConfigurationResultsBuilder, graphResults);

        boolean selectFromAllVariants = false;
        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, selectFromAllVariants, resolveState.failures, artifactResults, resolveState.fileDependencyResults, transientConfigurationResultsFactory, artifactTransforms, buildOperationExecutor, dependencyVerificationOverride, workerLeaseService);
        results.artifactsResolved(new DefaultResolvedConfiguration(result), result);
    }

    private static class ArtifactResolveState {
        final ResolvedGraphResults graphResults;
        final VisitedArtifactsResults artifactsResults;
        final VisitedFileDependencyResults fileDependencyResults;
        final Set<UnresolvedDependency> failures;
        final TransientConfigurationResultsBuilder transientConfigurationResultsBuilder;

        ArtifactResolveState(ResolvedGraphResults graphResults, VisitedArtifactsResults artifactsResults, VisitedFileDependencyResults fileDependencyResults, Set<UnresolvedDependency> failures, TransientConfigurationResultsBuilder transientConfigurationResultsBuilder) {
            this.graphResults = graphResults;
            this.artifactsResults = artifactsResults;
            this.fileDependencyResults = fileDependencyResults;
            this.failures = failures;
            this.transientConfigurationResultsBuilder = transientConfigurationResultsBuilder;
        }
    }

    class DependencyResultContainer implements Serializable {
        VisitedArtifactsResults artifactsResults;
        VisitedFileDependencyResults fileDependencyResults;
        Map<Long, SerializableDependency> graphResults;
        public DependencyResultContainer(VisitedArtifactsResults artifactsResults ,
                                  VisitedFileDependencyResults fileDependencyResults,
                                         Map<Long, SerializableDependency> graphResults)
        {
            this.artifactsResults = artifactsResults;
            this.fileDependencyResults = fileDependencyResults;
            this.graphResults = graphResults;
        }

        public VisitedArtifactsResults getArtifactsResults() {
            return artifactsResults;
        }

        public void setArtifactsResults(VisitedArtifactsResults artifactsResults) {
            this.artifactsResults = artifactsResults;
        }

        public VisitedFileDependencyResults getFileDependencyResults() {
            return fileDependencyResults;
        }

        public void setFileDependencyResults(VisitedFileDependencyResults fileDependencyResults) {
            this.fileDependencyResults = fileDependencyResults;
        }

        public Map<Long, SerializableDependency> getGraphResults() {
            return graphResults;
        }

        public void setGraphResults(Map<Long, SerializableDependency> graphResults) {
            this.graphResults = graphResults;
        }
    }

    class CacheableArtifactDependencies implements CacheableEntity {
        private String identity;
        DefaultBinaryStore newStore;
        DefaultBinaryStore oldStore;
        File graphFile;
        File artifactFile;
        File dependencyFile;

        public CacheableArtifactDependencies(
            String identity,
            DefaultBinaryStore newStore,
            DefaultBinaryStore oldStore,
            File graphFile,
            File artifactFile,
            File dependencyFile

        ) {
            this.identity = identity;
            this.newStore = newStore;
            this.oldStore = oldStore;
            this.graphFile =graphFile;
            this.artifactFile =artifactFile;
            this.dependencyFile =dependencyFile;
        }

        public String getIdentity() {return identity;}

        public Class<?> getType() {return this.getClass();}

        public String getDisplayName() {return identity;}

        public void visitOutputTrees(CacheableTreeVisitor visitor) {
            visitor.visitOutputTree("old", TreeType.FILE, oldStore.getFile());
            visitor.visitOutputTree("new", TreeType.FILE, newStore.getFile());
            visitor.visitOutputTree("artifact", TreeType.FILE, artifactFile);
            visitor.visitOutputTree("dependency", TreeType.FILE, dependencyFile);
            visitor.visitOutputTree("graph", TreeType.FILE, graphFile);
        }
    }

    class DependencyBuildCacheKey implements BuildCacheKey {
        private String hashCode;

        DependencyBuildCacheKey(String hashCode) {
            this.hashCode = hashCode;
        }

        public String getHashCode() {
            return hashCode;
        }

        public byte[] toByteArray() {
            return hashCode.getBytes();
        }

        public String getDisplayName() {
            return getHashCode();
        }
    }
}




