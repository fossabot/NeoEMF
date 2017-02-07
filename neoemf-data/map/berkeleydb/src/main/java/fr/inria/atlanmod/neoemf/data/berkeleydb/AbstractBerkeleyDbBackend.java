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

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import fr.inria.atlanmod.neoemf.core.Id;
import fr.inria.atlanmod.neoemf.core.PersistentEObject;
import fr.inria.atlanmod.neoemf.data.AbstractPersistenceBackend;
import fr.inria.atlanmod.neoemf.data.berkeleydb.serializer.Serializer;
import fr.inria.atlanmod.neoemf.data.structure.ContainerValue;
import fr.inria.atlanmod.neoemf.data.structure.FeatureKey;
import fr.inria.atlanmod.neoemf.data.structure.MetaclassValue;
import fr.inria.atlanmod.neoemf.data.structure.MultivaluedFeatureKey;
import fr.inria.atlanmod.neoemf.util.logging.NeoLogger;

import org.eclipse.emf.ecore.EClass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * ???
 */
public abstract class AbstractBerkeleyDbBackend extends AbstractPersistenceBackend implements BerkeleyDbBackend {

    /**
     * ???
     */
    protected final DatabaseConfig databaseConfig;

    /**
     * ???
     */
    private final EnvironmentConfig environmentConfig;

    /**
     * ???
     */
    private final File file;

    /**
     * A persistent map that stores the container of {@link PersistentEObject}, identified by the object {@link Id}.
     */
    protected Database containers;

    /**
     * A persistent map that stores the {@link EClass} for {@link PersistentEObject}, identified by the object {@link
     * Id}.
     */
    protected Database instances;

    /**
     * A persistent map that stores structural features values for {@link PersistentEObject}, identified by the
     * associated {@link FeatureKey}.
     */
    protected Database features;

    /**
     * ???
     */
    protected Environment environment;

    /**
     * ???
     */
    protected boolean isClosed = true;

    /**
     * ???
     *
     * @param file      ???
     * @param envConfig ???
     * @param dbConfig  ???
     */
    protected AbstractBerkeleyDbBackend(File file, EnvironmentConfig envConfig, DatabaseConfig dbConfig) {
        this.file = file;
        this.environmentConfig = envConfig;
        this.databaseConfig = dbConfig;
    }

    /**
     * ???
     */
    @Override
    public void open() {
        if (!isClosed()) {
            NeoLogger.warn("This backend is already open");
        }

        environment = new Environment(file, environmentConfig);

        containers = environment.openDatabase(null, "eContainer", databaseConfig);
        instances = environment.openDatabase(null, "neoInstanceOf", databaseConfig);
        features = environment.openDatabase(null, "features", databaseConfig);

        isClosed = false;
    }

    /**
     * Copies all the contents of this {@code PersistenceBackend} to the {@code target} one.
     *
     * @param target the {@code PersistenceBackend} to copy the database contents to
     */
    @Override
    public void copyTo(BerkeleyDbBackend target) {
        AbstractBerkeleyDbBackend backend = (AbstractBerkeleyDbBackend) target;

        copyDatabaseTo(instances, backend.instances);
        copyDatabaseTo(features, backend.features);
        copyDatabaseTo(containers, backend.containers);
    }

    @Override
    public void save() {
        allDatabases().forEach(Database::sync);
    }

    @Override
    public void close() {
        this.save();

        allDatabases().forEach(Database::close);

        environment.close();
        isClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public boolean isDistributed() {
        return false;
    }

    @Override
    public void create(Id id) {
        // Do nothing
    }

    @Override
    public boolean has(Id id) {
        return false;
    }

    @Override
    public Optional<ContainerValue> containerOf(Id id) {
        return fromDatabase(containers, id);
    }

    @Override
    public void containerFor(Id id, ContainerValue container) {
        toDatabase(containers, id, container);
    }

    @Override
    public Optional<MetaclassValue> metaclassOf(Id id) {
        return fromDatabase(instances, id);
    }

    @Override
    public void metaclassFor(Id id, MetaclassValue metaclass) {
        toDatabase(instances, id, metaclass);
    }

    @Override
    public <T> Optional<T> valueOf(FeatureKey key) {
        return fromDatabase(features, key);
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

        toDatabase(features, key, value);

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
        outDatabase(features, key);
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
        return fromDatabase(features, key).isPresent();
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
    public void addReference(MultivaluedFeatureKey key, Id id) {
        addValue(key, id);
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
    public boolean containsReference(FeatureKey key, Id id) {
        return containsValue(key, id);
    }

    @Override
    public OptionalInt indexOfReference(FeatureKey key, Id id) {
        return indexOfValue(key, id);
    }

    @Override
    public OptionalInt lastIndexOfReference(FeatureKey key, Id id) {
        return lastIndexOfValue(key, id);
    }

    @Override
    public Iterable<Id> referencesAsList(FeatureKey key) {
        return valuesAsList(key);
    }

    /**
     * Returns all loaded databases.
     *
     * @return a list of {@link Database}
     */
    protected List<Database> allDatabases() {
        List<Database> databases = new ArrayList<>();
        databases.add(containers);
        databases.add(instances);
        databases.add(features);
        return databases;
    }

    /**
     * Retrieves a value from the {@code database} according to the given {@code key}.
     *
     * @param database  the database where to looking for
     * @param key       the key of the element to retrieve
     * @param <K>       the type of the key
     * @param <V>       the type of the value
     *
     * @return on {@link Optional} containing the element, or an empty {@link Optional} if the element has not been
     * found
     */
    protected <K, V> Optional<V> fromDatabase(Database database, K key) {
        DatabaseEntry dbKey = new DatabaseEntry(new Serializer<K>().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry();

        Optional<V> value;
        if (database.get(null, dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            value = Optional.of(new Serializer<V>().deserialize(dbValue.getData()));
        }
        else {
            value = Optional.empty();
        }
        return value;
    }

    /**
     * Saves a {@code value} identified by the {@code key} in the {@code database}.
     *
     * @param database the database where to save the value
     * @param key      the key of the element to save
     * @param value    the value to save
     * @param <K>      the type of the key
     * @param <V>      the type of the value
     */
    protected <K, V> void toDatabase(Database database, K key, V value) {
        DatabaseEntry dbKey = new DatabaseEntry(new Serializer<K>().serialize(key));
        DatabaseEntry dbValue = new DatabaseEntry(new Serializer<V>().serialize(value));

        database.put(null, dbKey, dbValue);
    }

    /**
     * Removes a value from the {@code database} according to its {@code key}.
     *
     * @param database the database where to remove the value
     * @param key      the key of the element to remove
     * @param <K>      the type of the key
     */
    protected <K> void outDatabase(Database database, K key) {
        DatabaseEntry dbKey = new DatabaseEntry(new Serializer<K>().serialize(key));

        database.delete(null, dbKey);
    }

    /**
     * Utility method to copy the contents from one database to another.
     *
     * @param from the database to copy
     * @param to   the database to copy the database contents to
     */
    protected void copyDatabaseTo(Database from, Database to) {
        try (Cursor cursor = from.openCursor(null, null)) {
            DatabaseEntry dbKey = new DatabaseEntry();
            DatabaseEntry dbValue = new DatabaseEntry();
            while (cursor.getNext(dbKey, dbValue, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                to.put(null, dbKey, dbValue);
            }
        }
        to.sync();
    }
}