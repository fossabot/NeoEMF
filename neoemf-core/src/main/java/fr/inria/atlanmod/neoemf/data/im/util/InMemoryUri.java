/*
 * Copyright (c) 2013-2018 Atlanmod, Inria, LS2N, and IMT Nantes.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 */

package fr.inria.atlanmod.neoemf.data.im.util;

import fr.inria.atlanmod.neoemf.bind.FactoryBinding;
import fr.inria.atlanmod.neoemf.data.im.InMemoryBackendFactory;
import fr.inria.atlanmod.neoemf.util.AbstractUriBuilder;
import fr.inria.atlanmod.neoemf.util.UriBuilder;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A {@link fr.inria.atlanmod.neoemf.util.UriBuilder} that creates specific resource URIs using an {@link
 * fr.inria.atlanmod.neoemf.data.im.InMemoryBackend}.
 *
 * @see InMemoryBackendFactory
 * @see fr.inria.atlanmod.neoemf.data.BackendFactoryRegistry
 * @see fr.inria.atlanmod.neoemf.resource.PersistentResourceFactory
 */
@FactoryBinding(factory = InMemoryBackendFactory.class)
@ParametersAreNonnullByDefault
public class InMemoryUri extends AbstractUriBuilder {

    /**
     * Constructs a new {@code InMemoryUri}.
     */
    private InMemoryUri() {
    }

    /**
     * Creates a new {@link fr.inria.atlanmod.neoemf.util.UriBuilder} with the pre-configured scheme.
     *
     * @return a new builder
     */
    @Nonnull
    @SuppressWarnings("unused") // Called dynamically
    public static UriBuilder builder() {
        return new InMemoryUri();
    }

    @Override
    public boolean supportsFile() {
        return true;
    }

    @Override
    public boolean supportsServer() {
        return false;
    }
}
