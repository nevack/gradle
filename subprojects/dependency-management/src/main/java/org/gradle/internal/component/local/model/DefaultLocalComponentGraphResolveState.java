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

package org.gradle.internal.component.local.model;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentMetadata> implements LocalComponentGraphResolveState {
    private final ConcurrentMap<LocalConfigurationMetadata, DefaultLocalVariantArtifactResolveState> variants = new ConcurrentHashMap<>();

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return getMetadata().getModuleVersionId();
    }

    @Override
    public LocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        return getMetadata().copy(componentIdentifier, artifacts);
    }

    public DefaultLocalComponentGraphResolveState(LocalComponentMetadata metadata) {
        super(metadata);
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return stateFor((LocalConfigurationMetadata) variant);
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        return stateFor((LocalConfigurationMetadata) variant);
    }

    private DefaultLocalVariantArtifactResolveState stateFor(LocalConfigurationMetadata configuration) {
        return variants.computeIfAbsent(configuration, c -> {
            return new DefaultLocalVariantArtifactResolveState(getMetadata(), configuration);
        });
    }

    private static class DefaultLocalVariantArtifactResolveState implements VariantArtifactResolveState, VariantArtifactGraphResolveMetadata {
        private final LocalComponentMetadata component;
        private final LocalConfigurationMetadata graphSelectedVariant;
        public DefaultLocalVariantArtifactResolveState(LocalComponentMetadata component, LocalConfigurationMetadata graphSelectedVariant) {
            this.component = component;
            this.graphSelectedVariant = graphSelectedVariant;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            return graphSelectedVariant.getArtifacts();
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            graphSelectedVariant.prepareToResolveArtifacts();
            return graphSelectedVariant.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            graphSelectedVariant.prepareToResolveArtifacts();
            final Set<? extends VariantResolveMetadata> allVariants;
            if (component.getVariantsForGraphTraversal().isPresent()) {
                allVariants = component.getVariantsForGraphTraversal().get().stream().map(LocalConfigurationMetadata.class::cast).flatMap(variant -> {
                    variant.prepareToResolveArtifacts();
                    return variant.getVariants().stream();
                }).collect(Collectors.toSet());
            } else {
                allVariants = graphSelectedVariant.getVariants();
            }
            return artifactSelector.resolveArtifacts(component, allVariants, graphSelectedVariant.getVariants(), exclusions, overriddenAttributes);
        }
    }
}
