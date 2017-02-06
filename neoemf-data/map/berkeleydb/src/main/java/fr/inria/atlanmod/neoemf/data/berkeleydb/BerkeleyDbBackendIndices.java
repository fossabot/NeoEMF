/*
 * Copyright (c) 2013-2017 Atlanmod INRIA LINA Mines Nantes.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlanmod INRIA LINA Mines Nantes - initial API and implementation
 */

package fr.inria.atlanmod.neoemf.data.berkeleydb;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import fr.inria.atlanmod.neoemf.annotations.Experimental;
import fr.inria.atlanmod.neoemf.core.Id;
import fr.inria.atlanmod.neoemf.data.berkeleydb.serializer.ClassInfoSerializer;
import fr.inria.atlanmod.neoemf.data.berkeleydb.serializer.ContainerInfoSerializer;
import fr.inria.atlanmod.neoemf.data.berkeleydb.serializer.FeatureKeySerializer;
import fr.inria.atlanmod.neoemf.data.berkeleydb.serializer.IdSerializer;
import fr.inria.atlanmod.neoemf.data.berkeleydb.serializer.ObjectSerializer;
import fr.inria.atlanmod.neoemf.data.structure.ContainerValue;
import fr.inria.atlanmod.neoemf.data.structure.FeatureKey;
import fr.inria.atlanmod.neoemf.data.structure.MetaclassValue;
import fr.inria.atlanmod.neoemf.data.structure.MultivaluedFeatureKey;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ???
 */
@Experimental
class BerkeleyDbBackendIndices extends AbstractBerkeleyDbBackend {

    /**
     * A persistent map that store the values of multi-valued features for {@link Id}, identified by the associated
     * {@link MultivaluedFeatureKey}.
     */
    private Database multivaluedFeatures;

    /**
     * Constructs a new {@code BerkeleyDbBackendIndices} on the given {@code file} with the given
     * {@code envConfig}.
     *
     * @param file      ???
     * @param envConfig ???
     * @param dbConfig  ???
     *
     * @note This constructor is protected. To create a new {@code BerkeleyDbBackendIndices} use {@link
     * BerkeleyDbBackendFactory#createPersistentBackend(java.io.File, Map)}.
     */
    protected BerkeleyDbBackendIndices(File file, EnvironmentConfig envConfig, DatabaseConfig dbConfig) {
        super(file, envConfig, dbConfig);
    }

    @Override
    public void open() {
        super.open();

        this.multivaluedFeatures = environment.openDatabase(null, "multivaluedFeatures", databaseConfig);
    }

    @Override
    public <P extends BerkeleyDbBackend> void copyTo(P target) {
        super.copyTo(target);

        BerkeleyDbBackendIndices backend = (BerkeleyDbBackendIndices) target;

        this.copyDatabaseTo(multivaluedFeatures, backend.multivaluedFeatures);
    }

    @Override
    public void save() {
        super.save();

        this.multivaluedFeatures.sync();
    }

    @Override
    public void close() {
        this.save();

        containers.close();
        instances.close();
        features.close();
        multivaluedFeatures.close();
        environment.close();

        isClosed = true;
    }

    @Override
    public Optional<ContainerValue> containerOf(Id id) {
        DatabaseEntry dbKey = new DatabaseEntry(new IdSerializer().serialize(id));
        DatabaseEntry dbValue = new DatabaseEntry();

        Optional<ContainerValue> container;
        if (containers.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            container = Optional.of(new ContainerInfoSerializer().deserialize(dbValue.getData()));
        }
        else {
            container = Optional.empty();
        }

        return container;
    }

    @Override
    public void containerFor(Id id, ContainerValue container) {
        DatabaseEntry dbKey = new DatabaseEntry(new IdSerializer().serialize(id));
        DatabaseEntry dbValue = new DatabaseEntry(new ContainerInfoSerializer().serialize(container));

        containers.put(null, dbKey, dbValue);
    }

    @Override
    public Optional<MetaclassValue> metaclassOf(Id id) {
        DatabaseEntry dbKey = new DatabaseEntry(new IdSerializer().serialize(id));
        DatabaseEntry dbValue = new DatabaseEntry();

        Optional<MetaclassValue> metaclass;
        if (instances.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            metaclass = Optional.of(new ClassInfoSerializer().deserialize(dbValue.getData()));
        }
        else {
            metaclass = Optional.empty();
        }

        return metaclass;
    }

    @Override
    public void metaclassFor(Id id, MetaclassValue metaclass) {
        DatabaseEntry dbKey = new DatabaseEntry(new IdSerializer().serialize(id));
        DatabaseEntry dbValue = new DatabaseEntry(new ClassInfoSerializer().serialize(metaclass));

        instances.put(null, dbKey, dbValue);
    }

    @Override
    public <T> Optional<T> valueOf(FeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        Optional<T> value;
        if (features.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            value = Optional.of(new ObjectSerializer<T>().deserialize(dbValue.getData()));
        }
        else {
            value = Optional.empty();
        }

        return value;
    }

    @Override
    public <T> Optional<T> valueOf(MultivaluedFeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        Optional<T> value;
        if (multivaluedFeatures.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            value = Optional.of(new ObjectSerializer<T>().deserialize(dbValue.getData()));
        }
        else {
            value = Optional.empty();
        }

        return value;
    }

    @Override
    public Optional<Id> referenceOf(FeatureKey key) {
        return valueOf(key);
    }

    @Override
    public Optional<Id> referenceOf(MultivaluedFeatureKey key) {
        return valueOf(key);
    }

    @Override
    public <T> Optional<T> valueFor(FeatureKey key, T value) {
        Optional<T> previousValue = valueOf(key);

        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry(new ObjectSerializer<T>().serialize(value));

        features.put(null, dbKey, dbValue);

        return previousValue;
    }

    @Override
    public <T> Optional<T> valueFor(MultivaluedFeatureKey key, T value) {
        Optional<T> previousValue = valueOf(key);

        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry(new ObjectSerializer<T>().serialize(value));

        multivaluedFeatures.put(null, dbKey, dbValue);

        return previousValue;
    }

    @Override
    public Optional<Id> referenceFor(FeatureKey key, Id id) {
        return valueFor(key, id);
    }

    @Override
    public Optional<Id> referenceFor(MultivaluedFeatureKey key, Id id) {
        return valueFor(key, id);
    }

    @Override
    public void unsetValue(FeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));

        features.delete(null, dbKey);
    }

    @Override
    public void unsetAllValues(FeatureKey key) {
        unsetValue(key);
    }

    @Override
    public void unsetReference(FeatureKey key) {
        unsetValue(key);
    }

    @Override
    public void unsetAllReferences(FeatureKey key) {
        unsetReference(key);
    }

    @Override
    public boolean hasValue(FeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        return features.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS;
    }

    @Override
    public boolean hasAnyValue(FeatureKey key) {
        return hasValue(key);
    }

    @Override
    public boolean hasReference(FeatureKey key) {
        return hasValue(key);
    }

    @Override
    public boolean hasAnyReference(FeatureKey key) {
        return hasReference(key);
    }

    @Override
    public <T> void addValue(MultivaluedFeatureKey key, T value) {
        int size = sizeOf(key.withoutPosition()).orElse(0);

        // TODO Replace by Stream
        for (int i = size - 1; i >= key.position(); i--) {
            valueFor(key.withPosition(i + 1), valueOf(key.withPosition(i)).orElse(null));
        }
        sizeFor(key.withoutPosition(), size + 1);

        valueFor(key, value);
    }

    @Override
    public void addReference(MultivaluedFeatureKey key, Id id) {
        int size = sizeOf(key.withoutPosition()).orElse(0);

        // TODO Replace by Stream
        for (int i = size - 1; i >= key.position(); i--) {
            referenceFor(key.withPosition(i + 1), referenceOf(key.withPosition(i)).orElse(null));
        }
        sizeFor(key.withoutPosition(), size + 1);

        referenceFor(key, id);
    }

    @Override
    public <T> Optional<T> removeValue(MultivaluedFeatureKey key) {
        Optional<T> previousValue = valueOf(key);

        int size = sizeOf(key.withoutPosition()).orElse(0);

        // Update indexes (element to remove is overwritten)
        // TODO Replace by Stream
        for (int i = key.position() + 1; i < size; i++) {
            valueFor(key.withPosition(i - 1), valueOf(key.withPosition(i)).orElse(null));
        }
        sizeFor(key.withoutPosition(), size - 1);

        return previousValue;
    }

    @Override
    public Optional<Id> removeReference(MultivaluedFeatureKey key) {
        Optional<Id> previousId = referenceOf(key);

        int size = sizeOf(key.withoutPosition()).orElse(0);

        // Update indexes (element to remove is overwritten)
        // TODO Replace by Stream
        for (int i = key.position() + 1; i < size; i++) {
            referenceFor(key.withPosition(i - 1), referenceOf(key.withPosition(i)).orElse(null));
        }
        sizeFor(key.withoutPosition(), size - 1);

        return previousId;
    }

    @Override
    public void cleanValues(FeatureKey key) {
        unsetValue(key);
    }

    @Override
    public void cleanReferences(FeatureKey key) {
        unsetReference(key);
    }

    @Override
    public <T> boolean containsValue(FeatureKey key, T value) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .anyMatch(i -> valueOf(key.withPosition(i)).map(v -> Objects.equals(v, value)).orElse(false));
    }

    @Override
    public boolean containsReference(FeatureKey key, Id id) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .anyMatch(i -> referenceOf(key.withPosition(i)).map(v -> Objects.equals(v, id)).orElse(false));
    }

    @Override
    public <T> OptionalInt indexOfValue(FeatureKey key, T value) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .filter(i -> valueOf(key.withPosition(i)).map(v -> Objects.equals(v, value)).orElse(false))
                .min();
    }

    @Override
    public OptionalInt indexOfReference(FeatureKey key, Id id) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .filter(i -> referenceOf(key.withPosition(i)).map(v -> Objects.equals(v, id)).orElse(false))
                .min();
    }

    @Override
    public <T> OptionalInt lastIndexOfValue(FeatureKey key, T value) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .filter(i -> valueOf(key.withPosition(i)).map(v -> Objects.equals(v, value)).orElse(false))
                .max();
    }

    @Override
    public OptionalInt lastIndexOfReference(FeatureKey key, Id id) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .filter(i -> referenceOf(key.withPosition(i)).map(v -> Objects.equals(v, id)).orElse(false))
                .max();
    }

    @Override
    public <T> Iterable<T> valuesAsList(FeatureKey key) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .mapToObj(i -> this.<T>valueOf(key.withPosition(i)).orElse(null))
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<Id> referencesAsList(FeatureKey key) {
        return IntStream.range(0, sizeOf(key).orElse(0))
                .mapToObj(i -> referenceOf(key.withPosition(i)).orElse(null))
                .collect(Collectors.toList());
    }

    @Override
    public OptionalInt sizeOf(FeatureKey key) {
        return valueOf(key)
                .map(v -> OptionalInt.of((int) v))
                .orElse(OptionalInt.empty());
    }

    protected void sizeFor(FeatureKey key, int size) {
        valueFor(key, size);
    }
}
