/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resolve.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultArtifactSelector implements ArtifactSelector {
    private final Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts = Maps.newHashMap();
    private final List<OriginArtifactSelector> selectors;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public DefaultArtifactSelector(List<OriginArtifactSelector> selectors, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.selectors = selectors;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public ArtifactSet resolveArtifacts(LocalFileDependencyMetadata fileDependencyMetadata) {
        return new FileDependencyArtifactSet(fileDependencyMetadata, artifactTypeRegistry, calculatedValueContainerFactory);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Set<? extends VariantResolveMetadata> allVariants, Set<? extends VariantResolveMetadata> legacyVariants, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
        // TODO: using allResolvedArtifacts isn't correct here
        ImmutableSet.Builder<ResolvedVariant> allResolvedVariants = ImmutableSet.builder();
        for (VariantResolveMetadata variant : allVariants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant.getIdentifier(), variant.asDescribable(), variant.getAttributes(), variant.getArtifacts(), variant.getCapabilities(), exclusions, component.getModuleVersionId(), component.getSources());
            allResolvedVariants.add(resolvedVariant);
        }
        ImmutableSet.Builder<ResolvedVariant> legacyResolvedVariants = ImmutableSet.builder();
        for (VariantResolveMetadata variant : legacyVariants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant.getIdentifier(), variant.asDescribable(), variant.getAttributes(), variant.getArtifacts(), variant.getCapabilities(), exclusions, component.getModuleVersionId(), component.getSources());
            legacyResolvedVariants.add(resolvedVariant);
        }

        ArtifactSet artifacts = null;
        for (OriginArtifactSelector selector : selectors) {
            artifacts = selector.resolveArtifacts(component, allResolvedVariants.build(), legacyResolvedVariants.build(), exclusions, overriddenAttributes);
            if (artifacts != null) {
                break;
            }
        }
        if (artifacts == null) {
            throw new IllegalStateException("No artifacts selected.");
        }
        return artifacts;
    }

    private ResolvedVariant toResolvedVariant(VariantResolveMetadata.Identifier identifier,
                                              DisplayName displayName,
                                              ImmutableAttributes variantAttributes,
                                              ImmutableList<? extends ComponentArtifactMetadata> artifacts,
                                              CapabilitiesMetadata capabilities,
                                              ExcludeSpec exclusions,
                                              ModuleVersionIdentifier ownerId,
                                              ModuleSources moduleSources) {
        // artifactsToResolve are those not excluded by their owning module
        List<? extends ComponentArtifactMetadata> artifactsToResolve = CollectionUtils.filter(artifacts,
                artifact -> !exclusions.excludesArtifact(ownerId.getModule(), artifact.getName())
        );

        boolean hasExcludedArtifact = artifactsToResolve.size() < artifacts.size();

        VariantResolveMetadata.Identifier resolvedIdentifier;
        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            resolvedIdentifier = null;
        } else {
            resolvedIdentifier = identifier;
        }
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variantAttributes, artifacts);
        return ArtifactSetFactory.toResolvedVariant(resolvedIdentifier, displayName, attributes, artifactsToResolve, capabilities, ownerId, moduleSources, allResolvedArtifacts, artifactResolver, calculatedValueContainerFactory);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes) {
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(component.getAttributes(), artifacts);
        return ArtifactSetFactory.adHocVariant(component.getId(), component.getModuleVersionId(), artifacts, component.getSources(), component.getAttributesSchema(), artifactResolver, allResolvedArtifacts, attributes, overriddenAttributes, calculatedValueContainerFactory);
    }
}
