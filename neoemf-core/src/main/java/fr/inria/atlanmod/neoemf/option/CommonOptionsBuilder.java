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

package fr.inria.atlanmod.neoemf.option;

import javax.annotation.Nonnull;

/**
 * A common {@link PersistenceOptionsBuilder} that creates common options that are available for all backend implementations.
 * <p>
 * All features are all optional: options can be created using all or none of them.
 * <p>
 * <b>NOTE:</b> This class is intended for testing and should not be used in standard use.
 */
public class CommonOptionsBuilder extends AbstractPersistenceOptionsBuilder<CommonOptionsBuilder, CommonOptions> {

    /**
     * Instantiates a new {@code CommonOptionsBuilder}.
     */
    protected CommonOptionsBuilder() {
    }

    /**
     * Constructs a new {@code CommonOptionsBuilder} instance.
     *
     * @return a new builder
     */
    @Nonnull
    public static CommonOptionsBuilder newBuilder() {
        return new CommonOptionsBuilder();
    }
}