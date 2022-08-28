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

package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.Attribute;
import org.gradle.internal.component.model.AttributeSelectionSchema;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Caches select methods of a {@link AttributeSelectionSchema} delegate. Intended to be used with
 * an externally-defined {@link Cache} so that multiple instances can share the same cache.
 */
public class CachingAttributeSelectionSchema implements AttributeSelectionSchema {
    private final Cache cache;
    private AttributeSelectionSchema delegate;
    public CachingAttributeSelectionSchema(Cache cache, AttributeSelectionSchema delegate) {
        this.cache = cache;
        this.delegate = delegate;
    }

    @Override
    public boolean hasAttribute(Attribute<?> attribute) {
        return delegate.hasAttribute(attribute);
    }

    @Override
    public Set<Object> disambiguate(Attribute<?> attribute, @Nullable Object requested, Set<Object> candidates) {
        return delegate.disambiguate(attribute, requested, candidates);
    }

    @Override
    public boolean matchValue(Attribute<?> attribute, Object requested, Object candidate) {
        return delegate.matchValue(attribute, requested, candidate);
    }

    @Nullable
    @Override
    public Attribute<?> getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requested) {
        // It's almost always the same attribute sets which are compared, so in order to avoid a lot of memory allocation
        // during computation of the intersection, we cache the result here.
        ExtraAttributesEntry entry = new ExtraAttributesEntry(candidateAttributeSets, requested);

        // Technically, caching this method can be wrong in some cases. The default implementation of `collectExtraAttributes`
        // relies on the underlying AttributeSchemas, which can change between instances of a given delegate. In reality, the
        // only way the underlying schemas are used is to find more strongly typed versions of attributes, so finding a cached
        // entry is not the end of the world. But, there is a chance that caching could lead to getting a technically incorrect result.
        // Unfortunately, caching in this manner gives a pretty large performance bump, and setting this straight makes us a fair
        // bit slower, so we will keep it this way.
        return cache.extraAttributesCache.computeIfAbsent(entry, key -> delegate.collectExtraAttributes(candidateAttributeSets, requested));
    }

    @Override
    public PrecedenceResult orderByPrecedence(ImmutableAttributes requested) {
        return delegate.orderByPrecedence(requested);
    }

    public static class Cache {
        private final Map<ExtraAttributesEntry, Attribute<?>[]> extraAttributesCache = new HashMap<>();
    }

    /**
     * A cache entry key, leveraging _identity_ as the key, because we do interning.
     * This is a performance optimization.
     */
    public static class ExtraAttributesEntry {
        private final ImmutableAttributes[] candidateAttributeSets;
        private final ImmutableAttributes requestedAttributes;
        private final int hashCode;

        private ExtraAttributesEntry(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requestedAttributes) {
            this.candidateAttributeSets = candidateAttributeSets;
            this.requestedAttributes = requestedAttributes;
            int hash = Arrays.hashCode(candidateAttributeSets);
            hash = 31 * hash + requestedAttributes.hashCode();
            this.hashCode = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ExtraAttributesEntry that = (ExtraAttributesEntry) o;
            if (requestedAttributes != that.requestedAttributes) {
                return false;
            }
            if (candidateAttributeSets.length != that.candidateAttributeSets.length) {
                return false;
            }
            for (int i = 0; i < candidateAttributeSets.length; i++) {
                if (candidateAttributeSets[i] != that.candidateAttributeSets[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
