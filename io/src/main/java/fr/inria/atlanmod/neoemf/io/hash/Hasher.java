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

package fr.inria.atlanmod.neoemf.io.hash;

/**
 * An object able to calculate the {@link HashCode} of a value.
 */
public interface Hasher {

    /**
     * Calculates the {@link HashCode} of the given {@code value}.
     *
     * @param value the value to has
     *
     * @return an hashcode
     */
    HashCode hash(String value);
}
