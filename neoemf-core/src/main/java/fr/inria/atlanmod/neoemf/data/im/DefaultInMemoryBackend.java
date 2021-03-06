/*
 * Copyright (c) 2013-2018 Atlanmod, Inria, LS2N, and IMT Nantes.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 */

package fr.inria.atlanmod.neoemf.data.im;

import fr.inria.atlanmod.neoemf.core.Id;
import fr.inria.atlanmod.neoemf.data.bean.ClassBean;
import fr.inria.atlanmod.neoemf.data.bean.SingleFeatureBean;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A {@link InMemoryBackend} that stores all elements in {@link Map}s.
 */
@ParametersAreNonnullByDefault
public class DefaultInMemoryBackend extends AbstractInMemoryBackend {

    /**
     * The number of instances of this class, used to identify each instance.
     */
    @Nonnull
    @Nonnegative
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * An in-memory map that stores the container of {@link fr.inria.atlanmod.neoemf.core.PersistentEObject}s,
     * identified by the object {@link Id}.
     */
    @Nonnull
    private final ChronicleMap<Id, SingleFeatureBean> containers;

    /**
     * An in-memory map that stores the meta-class for {@link fr.inria.atlanmod.neoemf.core.PersistentEObject}s,
     * identified by the object {@link Id}.
     */
    @Nonnull
    private final ChronicleMap<Id, ClassBean> instances;

    /**
     * An in-memory map that stores all feature values for {@link fr.inria.atlanmod.neoemf.core.PersistentEObject}s,
     * identified by the associated {@link SingleFeatureBean}. Many-feature values are grouped in collections.
     */
    @Nonnull
    private final ChronicleMap<SingleFeatureBean, Object> features;

    /**
     * Constructs a new {@code DefaultInMemoryBackend}.
     */
    public DefaultInMemoryBackend() {
        final int id = COUNTER.getAndIncrement();
        final String prefix = "default/";

        containers = ChronicleMapBuilder.of(Id.class, SingleFeatureBean.class)
                .name(prefix + id + "/containers")
                .entries(Sizes.ENTRIES)
                .averageKeySize(Sizes.ID)
                .averageValueSize(Sizes.FEATURE)
                .keyMarshaller(new BeanMarshaller<>(SERIALIZER_FACTORY.forId()))
                .valueMarshaller(new BeanMarshaller<>(SERIALIZER_FACTORY.forSingleFeature()))
                .create();

        instances = ChronicleMapBuilder.of(Id.class, ClassBean.class)
                .name(prefix + id + "/instances")
                .entries(Sizes.ENTRIES)
                .averageKeySize(Sizes.ID)
                .averageValueSize(Sizes.CLASS)
                .keyMarshaller(new BeanMarshaller<>(SERIALIZER_FACTORY.forId()))
                .valueMarshaller(new BeanMarshaller<>(SERIALIZER_FACTORY.forClass()))
                .create();

        features = ChronicleMapBuilder.of(SingleFeatureBean.class, Object.class)
                .name(prefix + id + "/features")
                .entries(Sizes.ENTRIES)
                .averageKeySize(Sizes.FEATURE)
                .averageValueSize(Sizes.FEATURE_VALUE)
                .keyMarshaller(new BeanMarshaller<>(SERIALIZER_FACTORY.forSingleFeature()))
                .create();
    }

    @Override
    protected void internalClose() {
        containers.clear();
        containers.close();

        instances.clear();
        instances.close();

        features.clear();
        features.close();
    }

    @Nonnull
    @Override
    protected Map<Id, SingleFeatureBean> containers() {
        return containers;
    }

    @Nonnull
    @Override
    protected Map<Id, ClassBean> instances() {
        return instances;
    }

    @Nonnull
    @Override
    protected Map<SingleFeatureBean, Object> features() {
        return features;
    }
}
