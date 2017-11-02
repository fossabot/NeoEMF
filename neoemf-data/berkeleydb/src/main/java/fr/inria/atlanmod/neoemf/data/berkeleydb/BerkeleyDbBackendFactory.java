/*
 * Copyright (c) 2013-2017 Atlanmod, Inria, LS2N, and IMT Nantes.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 */

package fr.inria.atlanmod.neoemf.data.berkeleydb;

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import fr.inria.atlanmod.commons.annotation.Static;
import fr.inria.atlanmod.neoemf.config.Config;
import fr.inria.atlanmod.neoemf.config.ImmutableConfig;
import fr.inria.atlanmod.neoemf.data.AbstractBackendFactory;
import fr.inria.atlanmod.neoemf.data.Backend;
import fr.inria.atlanmod.neoemf.data.BackendFactory;
import fr.inria.atlanmod.neoemf.data.InvalidBackendException;
import fr.inria.atlanmod.neoemf.data.berkeleydb.config.BerkeleyDbConfig;

import org.eclipse.emf.common.util.URI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import static fr.inria.atlanmod.commons.Preconditions.checkArgument;

/**
 * A {@link BackendFactory} that creates {@link BerkeleyDbBackend} instances.
 */
@ParametersAreNonnullByDefault
public class BerkeleyDbBackendFactory extends AbstractBackendFactory {

    /**
     * The literal description of the factory.
     */
    private static final String NAME = "berkeleydb";

    /**
     * Constructs a new {@code BerkeleyDbBackendFactory}.
     */
    protected BerkeleyDbBackendFactory() {
    }

    /**
     * Returns the instance of this class.
     *
     * @return the instance of this class
     */
    @Nonnull
    public static BackendFactory getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Nonnull
    @Override
    public Backend createBackend(URI uri, ImmutableConfig baseConfig) {
        BerkeleyDbBackend backend;

        checkArgument(uri.isFile(), "%s only supports file-based URIs", getClass().getSimpleName());

        try {
            Path baseDirectory = Paths.get(uri.toFileString());

            // Merge and check conflicts between the two configurations
            ImmutableConfig mergedConfig = Config.load(baseDirectory)
                    .orElseGet(BerkeleyDbConfig::newConfig)
                    .merge(baseConfig);

            String mapping = mergedConfig.getMapping();
            boolean isReadOnly = mergedConfig.isReadOnly();

            if (Files.notExists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
            }

            EnvironmentConfig environmentConfig = new EnvironmentConfig()
                    .setAllowCreate(!isReadOnly)
                    .setReadOnly(isReadOnly);

            Environment environment = new Environment(baseDirectory.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig()
                    .setAllowCreate(!isReadOnly)
                    .setReadOnly(isReadOnly)
                    .setSortedDuplicates(false)
                    .setDeferredWrite(true);

            backend = createMapper(mapping, environment, databaseConfig);

            mergedConfig.save(baseDirectory);
        }
        catch (Exception e) {
            throw new InvalidBackendException("Unable to open the BerkeleyDB database", e);
        }

        return backend;
    }

    /**
     * The initialization-on-demand holder of the singleton of this class.
     */
    @Static
    private static final class Holder {

        /**
         * The instance of the outer class.
         */
        static final BackendFactory INSTANCE = new BerkeleyDbBackendFactory();
    }
}
