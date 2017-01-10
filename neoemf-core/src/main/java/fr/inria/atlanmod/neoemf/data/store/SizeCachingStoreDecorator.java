/*
 * Copyright (c) 2013-2016 Atlanmod INRIA LINA Mines Nantes.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlanmod INRIA LINA Mines Nantes - initial API and implementation
 */

package fr.inria.atlanmod.neoemf.data.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import fr.inria.atlanmod.neoemf.data.structure.FeatureKey;

import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A {@link PersistentStore} decorator that caches the size data.
 */
public class SizeCachingStoreDecorator extends AbstractPersistentStoreDecorator {

    private final Cache<FeatureKey, Integer> sizesCache;

    public SizeCachingStoreDecorator(PersistentStore store) {
        this(store, 10000);
    }

    public SizeCachingStoreDecorator(PersistentStore store, int cacheSize) {
        super(store);
        this.sizesCache = Caffeine.newBuilder().maximumSize(cacheSize).build();
    }

    @Override
    public void unset(InternalEObject internalObject, EStructuralFeature feature) {
        FeatureKey featureKey = FeatureKey.from(internalObject, feature);
        sizesCache.put(featureKey, 0);
        super.unset(internalObject, feature);
    }

    @Override
    public boolean isEmpty(InternalEObject internalObject, EStructuralFeature feature) {
        FeatureKey featureKey = FeatureKey.from(internalObject, feature);
        Integer size = sizesCache.getIfPresent(featureKey);
        return isNull(size) ? super.isEmpty(internalObject, feature) : (size == 0);
    }

    @Override
    public int size(InternalEObject internalObject, EStructuralFeature feature) {
        FeatureKey featureKey = FeatureKey.from(internalObject, feature);
        return sizesCache.get(featureKey, key -> super.size(internalObject, feature));
    }

    @Override
    public void add(InternalEObject internalObject, EStructuralFeature feature, int index, Object value) {
        FeatureKey featureKey = FeatureKey.from(internalObject, feature);
        Integer size = sizesCache.getIfPresent(featureKey);
        if (nonNull(size)) {
            sizesCache.put(featureKey, size + 1);
        }
        super.add(internalObject, feature, index, value);
    }

    @Override
    public Object remove(InternalEObject internalObject, EStructuralFeature feature, int index) {
        FeatureKey featureKey = FeatureKey.from(internalObject, feature);
        Integer size = sizesCache.getIfPresent(featureKey);
        if (nonNull(size)) {
            sizesCache.put(featureKey, size - 1);
        }
        return super.remove(internalObject, feature, index);
    }

    @Override
    public void clear(InternalEObject internalObject, EStructuralFeature feature) {
        FeatureKey featureKey = FeatureKey.from(internalObject, feature);
        sizesCache.put(featureKey, 0);
        super.clear(internalObject, feature);
    }
}
