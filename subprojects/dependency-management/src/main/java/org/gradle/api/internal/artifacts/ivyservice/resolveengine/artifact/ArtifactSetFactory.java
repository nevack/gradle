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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ArtifactSetFactory {
    public static ArtifactSet createFromVariantMetadata(ComponentIdentifier componentIdentifier,
                                                        ModuleVersionIdentifier ownerId,
                                                        ModuleSources moduleSources,
                                                        ExcludeSpec exclusions,
                                                        Set<? extends VariantResolveMetadata> allVariants,
                                                        Set<? extends VariantResolveMetadata> legacyVariants,
                                                        AttributesSchemaInternal schema,
                                                        ArtifactResolver artifactResolver,
                                                        Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts,
                                                        ArtifactTypeRegistry artifactTypeRegistry,
                                                        ImmutableAttributes selectionAttributes,
                                                        CalculatedValueContainerFactory calculatedValueContainerFactory) {
        ImmutableSet.Builder<ResolvedVariant> legacyResolvedVariants = ImmutableSet.builder();
        for (VariantResolveMetadata variant : legacyVariants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
            legacyResolvedVariants.add(resolvedVariant);
        }
        ImmutableSet.Builder<ResolvedVariant> allResolvedVariants = ImmutableSet.builder();
        for (VariantResolveMetadata variant : allVariants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
            legacyResolvedVariants.add(resolvedVariant);
        }
        return new DefaultArtifactSet(componentIdentifier, schema, selectionAttributes, allResolvedVariants.build(), legacyResolvedVariants.build());
    }

    public static ArtifactSet adHocVariant(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, Collection<? extends ComponentArtifactMetadata> artifacts, ModuleSources moduleSources, ExcludeSpec exclusions, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes variantAttributes, ImmutableAttributes selectionAttributes, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        VariantResolveMetadata.Identifier identifier = null;
        if (artifacts.size() == 1) {
            identifier = new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId());
        }
        VariantResolveMetadata variantMetadata = new DefaultVariantMetadata(null, identifier, Describables.of(componentIdentifier), variantAttributes, ImmutableList.copyOf(artifacts), ImmutableCapabilities.EMPTY);
        ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
        return new DefaultArtifactSet(componentIdentifier, schema, selectionAttributes, Collections.singleton(resolvedVariant), Collections.singleton(resolvedVariant));
    }

    private static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        // Apply any artifact type mappings to the attributes of the variant
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant.getAttributes().asImmutable(), variant.getArtifacts());
        return toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, attributes, calculatedValueContainerFactory);
    }

    private static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ImmutableAttributes variantAttributes, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        List<? extends ComponentArtifactMetadata> artifacts = variant.getArtifacts();

        // artifactsToResolve are those not excluded by their owning module
        List<? extends ComponentArtifactMetadata> artifactsToResolve = CollectionUtils.filter(artifacts,
            artifact -> !exclusions.excludesArtifact(ownerId.getModule(), artifact.getName())
        );

        boolean hasExcludedArtifact = artifactsToResolve.size() < artifacts.size();

        VariantResolveMetadata.Identifier identifier = variant.getIdentifier();
        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            identifier = null;
        }

        return ArtifactBackedResolvedVariant.create(identifier, variant.asDescribable(), variantAttributes, variant.getCapabilities(), supplyLazilyResolvedArtifacts(ownerId, moduleSources, artifactsToResolve, artifactResolver, allResolvedArtifacts, calculatedValueContainerFactory));
    }

    private static Supplier<Collection<? extends ResolvableArtifact>> supplyLazilyResolvedArtifacts(ModuleVersionIdentifier ownerId, ModuleSources moduleSources, List<? extends ComponentArtifactMetadata> artifacts, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        return () -> {
            ImmutableSet.Builder<ResolvableArtifact> resolvedArtifacts = ImmutableSet.builder();
            for (ComponentArtifactMetadata artifact : artifacts) {
                ResolvableArtifact resolvedArtifact = allResolvedArtifacts.get(artifact.getId());
                if (resolvedArtifact == null) {
                    if (artifact.isOptionalArtifact()) {
                        DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
                        artifactResolver.resolveArtifact(artifact, moduleSources, result);
                        if (!result.isSuccessful()) {
                            // Optional artifact is not available
                            continue;
                        }
                    }
                    ValueCalculator<File> artifactCalculator;
                    if (artifactResolver instanceof ProjectArtifactResolver) {
                        artifactCalculator = ((ProjectArtifactResolver) artifactResolver).resolveArtifactLater(artifact);
                    } else {
                        // TODO - push this up to all ArtifactResolver implementations
                        artifactCalculator = new LazyArtifactSupplier(artifact, moduleSources, artifactResolver);
                    }
                    CalculatedValue<File> artifactSource = calculatedValueContainerFactory.create(Describables.of(artifact.getId()), artifactCalculator);
                    resolvedArtifact = new DefaultResolvableArtifact(ownerId, artifact.getName(), artifact.getId(), context -> context.add(artifact.getBuildDependencies()), artifactSource, calculatedValueContainerFactory);
                    allResolvedArtifacts.put(artifact.getId(), resolvedArtifact);
                }
                resolvedArtifacts.add(resolvedArtifact);
            }
            return resolvedArtifacts.build();
        };
    }

    private static class SingleArtifactVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final ComponentArtifactIdentifier artifactIdentifier;

        public SingleArtifactVariantIdentifier(ComponentArtifactIdentifier artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public int hashCode() {
            return artifactIdentifier.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            SingleArtifactVariantIdentifier other = (SingleArtifactVariantIdentifier) obj;
            return artifactIdentifier.equals(other.artifactIdentifier);
        }
    }

    private static class LazyArtifactSupplier implements ValueCalculator<File> {
        private final ArtifactResolver artifactResolver;
        private final ModuleSources moduleSources;
        private final ComponentArtifactMetadata artifact;

        private LazyArtifactSupplier(ComponentArtifactMetadata artifact, ModuleSources moduleSources, ArtifactResolver artifactResolver) {
            this.artifact = artifact;
            this.artifactResolver = artifactResolver;
            this.moduleSources = moduleSources;
        }

        @Override
        public File calculateValue(NodeExecutionContext context) {
            DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifact, moduleSources, result);
            return result.getResult();
        }
    }
}
