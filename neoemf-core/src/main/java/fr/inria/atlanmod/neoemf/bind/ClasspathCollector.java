/*
 * Copyright (c) 2013-2018 Atlanmod, Inria, LS2N, and IMT Nantes.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 */

package fr.inria.atlanmod.neoemf.bind;

import fr.inria.atlanmod.commons.Throwables;
import fr.inria.atlanmod.commons.annotation.Singleton;
import fr.inria.atlanmod.commons.annotation.Static;
import fr.inria.atlanmod.commons.concurrent.MoreExecutors;
import fr.inria.atlanmod.commons.function.Converter;
import fr.inria.atlanmod.commons.log.Log;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.stream.Collectors;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;

/**
 * An {@link URLCollector} that analyzes a {@link java.net.URLClassLoader} to retrieve all modules related to this one.
 * This is the main {@link URLCollector} used by {@link BindingEngine} to retrieve associated object instances.
 */
@Singleton
@ParametersAreNonnullByDefault
public class ClasspathCollector implements URLCollector {

    /**
     * The annotation used to determine the relation with NeoEMF.
     */
    @Nonnull
    private static final Class<? extends Annotation> BASE_ANNOTATION = FactoryBinding.class;

    /**
     * The identifier of the phase 'ready-to-use' of the {@link #phaser}. In this state, no analysis is in progress.
     */
    @Nonnegative
    private static final int READY = 0;

    /**
     * The {@link Converter} used to manipulate {@link URI}s instead of {@link URL}s.
     * <p>
     * Prefer to store {@link URI}s because of the {@link URL#equals(Object)} method.
     */
    @Nonnull
    private final Converter<URL, URI> urlToUri = new Converter<URL, URI>() {
        @Override
        public URI convert(URL url) {
            try {
                return url.toURI();
            }
            catch (URISyntaxException e) {
                throw Throwables.shouldNeverHappen(e);
            }
        }

        @Override
        public URL revert(URI uri) {
            try {
                return uri.toURL();
            }
            catch (MalformedURLException e) {
                throw Throwables.shouldNeverHappen(e);
            }
        }
    };

    /**
     * The concurrent pool for managing classpath analysis tasks.
     */
    @Nonnull
    private final ExecutorService analysisPool = MoreExecutors.newFixedThreadPool("binding-analyzer");

    /**
     * A phaser representing the number of classpath analysis in progress. When {@code phaser.getPhase() > 0}, at least
     * one analysis is in progress, and all calls to {@link #get()} will wait for all tasks to complete.
     */
    @Nonnull
    private final Phaser phaser = new Phaser(READY);

    /**
     * A set that holds the registered {@link URL}s.
     */
    @Nonnull
    private final Set<URI> registeredUris = Collections.synchronizedSet(new HashSet<>());

    /**
     * Returns the instance of this class.
     *
     * @return the instance of this class
     */
    @Nonnull
    public static ClasspathCollector getInstance() {
        return Holder.INSTANCE;
    }

    @Nonnull
    @Override
    public Set<URL> get() {
        Log.debug("Waiting for the classpath analysis... [phase = {0}]", phaser.getRegisteredParties());

        // Waiting for classpath analysis
        phaser.awaitAdvance(READY);
        Log.debug("Classpath analysis is complete [phase = {0}]", phaser.getRegisteredParties());

        return registeredUris.stream().map(urlToUri::revert).collect(Collectors.toSet());
    }

    /**
     * Scans all URLs provided by the given {@code callable} and retain only those that can be related to NeoEMF.
     *
     * @param urlCollector the {@link URLCollector} to analyze
     *
     * @throws NullPointerException if {@code urlCollector} is {@code null}
     */
    public void register(URLCollector urlCollector) {
        checkNotNull(urlCollector, "urlCollector");

        phaser.register();
        Log.debug("Starting a classpath analysis... [phase = {0}]", phaser.getRegisteredParties());

        analysisPool.submit(() -> {
            try {
                // One configuration for one task
                ConfigurationBuilder baseConfig = new ConfigurationBuilder()
                        .setExecutorService(BindingEngine.getBindingPool())
                        .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner());

                // Filter URLs, and remove any that cannot be related to NeoEMF (Java, EMF,...)
                checkNotNull(urlCollector.get(), "urlCollector.get").stream()
                        .filter(url -> isRelated(url, baseConfig))
                        .map(urlToUri::convert)
                        .collect(Collectors.toCollection(() -> registeredUris));
            }
            catch (Exception e) {
                Log.warn(e);
            }
            finally {
                phaser.arriveAndDeregister();
                Log.debug("Stopping a classpath analysis [phase = {0}]", phaser.getRegisteredParties());
            }
        });
    }

    /**
     * Returns {@code true} if the {@code url} contains any element related to NeoEMF.
     *
     * @param url        the url to scan
     * @param baseConfig the base configuration of the classpath scanning
     *
     * @return {@code true} if the {@code url} contains any element related to NeoEMF
     */
    private boolean isRelated(URL url, ConfigurationBuilder baseConfig) {
        try {
            return !new Reflections(baseConfig.setUrls(url)).getTypesAnnotatedWith(BASE_ANNOTATION).isEmpty();
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * The initialization-on-demand holder of the singleton of this class.
     */
    @Static
    private static final class Holder {

        /**
         * The instance of the outer class.
         */
        static final ClasspathCollector INSTANCE = new ClasspathCollector();
    }
}
