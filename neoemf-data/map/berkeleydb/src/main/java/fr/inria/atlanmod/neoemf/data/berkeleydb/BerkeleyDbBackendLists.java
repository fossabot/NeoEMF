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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * ???
 */
@Experimental
class BerkeleyDbBackendLists extends AbstractBerkeleyDbBackend {

    /**
     * Constructs a new {@code BerkeleyDbBackendIndices} on the given {@code file} with the given
     * {@code envConfig}.
     *
     * @param file      ???
     * @param envConfig ???
     * @param dbConfig  ???
     *
     * @note This constructor is protected. To create a new {@code BerkeleyDbBackendLists} use {@link
     * BerkeleyDbBackendFactory#createPersistentBackend(File, Map)}.
     */
    protected BerkeleyDbBackendLists(File file, EnvironmentConfig envConfig, DatabaseConfig dbConfig) {
        super(file, envConfig, dbConfig);
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
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key.withoutPosition()));
        DatabaseEntry dbValue = new DatabaseEntry();

        Optional<T> value;
        if (features.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            value = Optional.of(new ObjectSerializer<List<T>>().deserialize(dbValue.getData()).get(key.position()));
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
    public <T> Optional<T> valueFor(FeatureKey key, T obj) {
        Optional<T> previousValue = valueOf(key);

        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry(new ObjectSerializer<T>().serialize(obj));

        features.put(null, dbKey, dbValue);

        return previousValue;
    }

    @Override
    public <T> Optional<T> valueFor(MultivaluedFeatureKey key, T value) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key.withoutPosition()));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        List<T> values = new ObjectSerializer<List<T>>().deserialize(dbValue.getData());

        Optional<T> previousValue = Optional.of(values.set(key.position(), value));

        dbValue = new DatabaseEntry(new ObjectSerializer<List<T>>().serialize(values));
        features.put(null, dbKey, dbValue);

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
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key.withoutPosition()));
        DatabaseEntry dbValue = new DatabaseEntry();

        List<T> values;
        if (features.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            values = new ObjectSerializer<List<T>>().deserialize(dbValue.getData());
        }
        else {
            values = new ArrayList<>(0);
        }

        values.add(key.position(), value);

        dbValue = new DatabaseEntry(new ObjectSerializer<List<T>>().serialize(values));
        features.put(null, dbKey, dbValue);
    }

    @Override
    public void addReference(MultivaluedFeatureKey key, Id id) {
        addValue(key, id);
    }

    @Override
    public <T> Optional<T> removeValue(MultivaluedFeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key.withoutPosition()));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        List<T> values = new ObjectSerializer<List<T>>().deserialize(dbValue.getData());

        Optional<T> previousValue = Optional.of(values.remove(key.position()));

        dbValue = new DatabaseEntry(new ObjectSerializer<List<T>>().serialize(values));
        features.put(null, dbKey, dbValue);

        return previousValue;
    }

    @Override
    public Optional<Id> removeReference(MultivaluedFeatureKey key) {
        return removeValue(key);
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
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        List<T> values = new ObjectSerializer<List<T>>().deserialize(dbValue.getData());

        return values.contains(value);
    }

    @Override
    public boolean containsReference(FeatureKey key, Id id) {
        return containsValue(key, id);
    }

    @Override
    public <T> OptionalInt indexOfValue(FeatureKey key, T value) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        List<T> values = new ObjectSerializer<List<T>>().deserialize(dbValue.getData());

        int index = values.indexOf(value);
        return index == -1 ? OptionalInt.empty() : OptionalInt.of(index);
    }

    @Override
    public OptionalInt indexOfReference(FeatureKey key, Id id) {
        return indexOfValue(key, id);
    }

    @Override
    public <T> OptionalInt lastIndexOfValue(FeatureKey key, T value) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        List<T> values = new ObjectSerializer<List<T>>().deserialize(dbValue.getData());

        int index = values.lastIndexOf(value);
        return index == -1 ? OptionalInt.empty() : OptionalInt.of(index);
    }

    @Override
    public OptionalInt lastIndexOfReference(FeatureKey key, Id id) {
        return lastIndexOfValue(key, id);
    }

    @Override
    public <T> Iterable<T> valuesAsList(FeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        return new ObjectSerializer<List<T>>().deserialize(dbValue.getData());
    }

    @Override
    public Iterable<Id> referencesAsList(FeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        features.get(null, dbKey, dbValue, LockMode.DEFAULT);
        return new ObjectSerializer<List<Id>>().deserialize(dbValue.getData());
    }

    @Override
    public <T> OptionalInt sizeOf(FeatureKey key) {
        DatabaseEntry dbKey = new DatabaseEntry(new FeatureKeySerializer().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        if (features.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            return OptionalInt.of(new ObjectSerializer<List<T>>().deserialize(dbValue.getData()).size());
        }
        else {
            return OptionalInt.empty();
        }
    }
}
