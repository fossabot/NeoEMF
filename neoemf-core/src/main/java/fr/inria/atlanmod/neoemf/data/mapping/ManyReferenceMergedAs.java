/*
 * Copyright (c) 2013-2018 Atlanmod, Inria, LS2N, and IMT Nantes.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 */

package fr.inria.atlanmod.neoemf.data.mapping;

import fr.inria.atlanmod.commons.function.Converter;
import fr.inria.atlanmod.neoemf.core.Id;
import fr.inria.atlanmod.neoemf.data.bean.ManyFeatureBean;
import fr.inria.atlanmod.neoemf.data.bean.SingleFeatureBean;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import static fr.inria.atlanmod.commons.Preconditions.checkNotContainsNull;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static fr.inria.atlanmod.commons.Preconditions.checkPositionIndex;
import static java.util.Objects.isNull;

/**
 * A {@link ManyReferenceMapper} that provides a default behavior to use {@code M} instead of a set of {@link Id} for
 * multi-valued references.
 * <p>
 * This mapper merges the multi-valued references into a single value.
 *
 * @param <M> the type of the multi-valued reference after mapping
 */
@ParametersAreNonnullByDefault
public interface ManyReferenceMergedAs<M> extends ValueMapper, ManyReferenceMapper {

    @Nonnull
    @Override
    default Optional<Id> referenceOf(ManyFeatureBean feature) {
        Converter<List<Id>, M> converter = manyReferenceMerger();

        return this.<M>valueOf(feature.withoutPosition())
                .map(converter::revert)
                .filter(ids -> feature.position() < ids.size())
                .map(ids -> ids.get(feature.position()));
    }

    @Nonnull
    @Override
    default Stream<Id> allReferencesOf(SingleFeatureBean feature) {
        Converter<List<Id>, M> converter = manyReferenceMerger();

        return this.<M>valueOf(feature)
                .map(converter::revert)
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    @Nonnull
    @Override
    default Optional<Id> referenceFor(ManyFeatureBean feature, Id reference) {
        checkNotNull(feature, "feature");
        checkNotNull(reference, "reference");

        Converter<List<Id>, M> converter = manyReferenceMerger();

        List<Id> ids = this.<M>valueOf(feature.withoutPosition())
                .map(converter::revert)
                .<NoSuchElementException>orElseThrow(NoSuchElementException::new);

        if (feature.position() >= ids.size()) {
            throw new NoSuchElementException();
        }

        Optional<Id> previousId = Optional.of(ids.get(feature.position()));

        ids.set(feature.position(), reference);

        valueFor(feature.withoutPosition(), converter.convert(ids));

        return previousId;
    }

    @Override
    default void addReference(ManyFeatureBean feature, Id reference) {
        checkNotNull(feature, "feature");
        checkNotNull(reference, "reference");

        Converter<List<Id>, M> converter = manyReferenceMerger();

        List<Id> ids = this.<M>valueOf(feature.withoutPosition())
                .map(converter::revert)
                .orElseGet(ArrayList::new);

        checkPositionIndex(feature.position(), ids.size());

        ids.add(feature.position(), reference);

        valueFor(feature.withoutPosition(), converter.convert(ids));
    }

    @Override
    default void addAllReferences(ManyFeatureBean feature, List<Id> references) {
        checkNotNull(feature, "feature");
        checkNotNull(references, "references");
        checkNotContainsNull(references, "references");

        if (references.isEmpty()) {
            return;
        }

        Converter<List<Id>, M> converter = manyReferenceMerger();

        List<Id> ids = this.<M>valueOf(feature.withoutPosition())
                .map(converter::revert)
                .orElseGet(ArrayList::new);

        int firstPosition = feature.position();
        checkPositionIndex(firstPosition, ids.size());

        ids.addAll(firstPosition, references);

        valueFor(feature.withoutPosition(), converter.convert(ids));
    }

    @Nonnull
    @Override
    default Optional<Id> removeReference(ManyFeatureBean feature) {
        checkNotNull(feature, "feature");

        Converter<List<Id>, M> converter = manyReferenceMerger();

        List<Id> ids = this.<M>valueOf(feature.withoutPosition())
                .map(converter::revert)
                .orElse(null);

        if (isNull(ids)) {
            return Optional.empty();
        }

        Optional<Id> previousId = Optional.empty();

        if (feature.position() < ids.size()) {
            previousId = Optional.of(ids.remove(feature.position()));

            if (ids.isEmpty()) {
                removeAllReferences(feature.withoutPosition());
            }
            else {
                valueFor(feature.withoutPosition(), converter.convert(ids));
            }
        }

        return previousId;
    }

    @Override
    default void removeAllReferences(SingleFeatureBean feature) {
        removeReference(feature);
    }

    @Nonnull
    @Nonnegative
    @Override
    default Optional<Integer> sizeOfReference(SingleFeatureBean feature) {
        Converter<List<Id>, M> converter = manyReferenceMerger();

        return this.<M>valueOf(feature)
                .map(converter::revert)
                .map(List::size)
                .filter(s -> s != 0);
    }

    /**
     * Returns the converter used to transform a ordered list of references to the desired type.
     *
     * @return the conveter
     */
    @Nonnull
    Converter<List<Id>, M> manyReferenceMerger();
}
