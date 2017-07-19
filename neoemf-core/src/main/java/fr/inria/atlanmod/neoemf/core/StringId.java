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

package fr.inria.atlanmod.neoemf.core;

import fr.inria.atlanmod.common.hash.Hasher;
import fr.inria.atlanmod.common.hash.Hashers;

import org.eclipse.emf.ecore.util.EcoreUtil;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

import static fr.inria.atlanmod.common.Preconditions.checkNotNull;

/**
 * An {@link Id} with a {@link String} representation.
 */
@Immutable
@ParametersAreNonnullByDefault
public class StringId implements Id {

    @SuppressWarnings("JavaDoc")
    private static final long serialVersionUID = 7727028860219819798L;

    /**
     * The {@link Hasher} used to generate {@link Id} from string.
     *
     * @see #generate(String)
     */
    @Nonnull
    private static final Hasher HASHER = Hashers.murmur128();

    /**
     * The literal representation of this {@code Id} as a {@link String}.
     */
    @Nonnull
    private final String literalId;

    /**
     * Constructs a new {@code StringId} with its literal representation.
     *
     * @param literalId the literal representation of this {@code StringId}
     */
    protected StringId(String literalId) {
        this.literalId = checkNotNull(literalId);
    }

    /**
     * Creates a new {@code StringId} with the given {@code object} by calling {@link Object#toString()}.
     *
     * @param object the object
     *
     * @return a new {@code StringId}
     */
    @Nonnull
    public static Id from(Object object) {
        return of(object.toString());
    }

    /**
     * Creates a new {@code StringId} with its literal representation.
     *
     * @param literalId the literal representation of this {@code StringId}
     *
     * @return a new {@code StringId}
     */
    @Nonnull
    public static Id of(String literalId) {
        return new StringId(literalId);
    }

    /**
     * Creates a new instance of an {@link Id} initialized with a {@link String} representation generated by using the
     * {@link EcoreUtil#generateUUID()} method.
     *
     * @return a new instance of an {@link Id}
     *
     * @see EcoreUtil#generateUUID()
     */
    @Nonnull
    public static Id generate() {
        return new StringId(EcoreUtil.generateUUID());
    }

    @Nonnull
    public static Id generate(String name) {
        final byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        return new StringId(HASHER.hash(bytes).toHexString());
    }

    @Override
    public int compareTo(Id o) {
        return o.toString().compareTo(toString());
    }

    @Override
    public long toLong() {
        throw new UnsupportedOperationException("This Id cannot have a Long representation");
    }

    @Override
    public int hashCode() {
        return Objects.hash(literalId);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!StringId.class.isInstance(o)) {
            return false;
        }

        StringId that = StringId.class.cast(o);
        return Objects.equals(literalId, that.literalId);
    }

    @Nonnull
    @Override
    public String toString() {
        return literalId;
    }
}
