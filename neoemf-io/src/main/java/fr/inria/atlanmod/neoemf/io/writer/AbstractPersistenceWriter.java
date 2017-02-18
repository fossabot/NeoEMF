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

package fr.inria.atlanmod.neoemf.io.writer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import fr.inria.atlanmod.neoemf.core.Id;
import fr.inria.atlanmod.neoemf.core.StringId;
import fr.inria.atlanmod.neoemf.data.PersistenceBackend;
import fr.inria.atlanmod.neoemf.data.structure.ContainerDescriptor;
import fr.inria.atlanmod.neoemf.data.structure.FeatureKey;
import fr.inria.atlanmod.neoemf.data.structure.MetaclassDescriptor;
import fr.inria.atlanmod.neoemf.io.AlreadyExistingIdException;
import fr.inria.atlanmod.neoemf.io.structure.RawAttribute;
import fr.inria.atlanmod.neoemf.io.structure.RawElement;
import fr.inria.atlanmod.neoemf.io.structure.RawId;
import fr.inria.atlanmod.neoemf.io.structure.RawMetaclass;
import fr.inria.atlanmod.neoemf.io.structure.RawReference;
import fr.inria.atlanmod.neoemf.io.util.PersistenceConstants;
import fr.inria.atlanmod.neoemf.io.util.hash.Hasher;
import fr.inria.atlanmod.neoemf.io.util.hash.HasherFactory;
import fr.inria.atlanmod.neoemf.util.logging.NeoLogger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.nonNull;

/**
 * A {@link PersistenceWriter} that persists data in a {@link PersistenceBackend}, based on received events.
 */
@ParametersAreNonnullByDefault
public abstract class AbstractPersistenceWriter implements PersistenceWriter {

    /**
     * The default cache size.
     *
     * @note It is calculated according to the maximum memory dedicated to the JVM.
     */
    private static final long DEFAULT_CACHE_SIZE = adaptFromMemory(2000L);

    /**
     * The default operation between commits.
     *
     * @note It is calculated according to the maximum memory dedicated to the JVM.
     */
    private static final long DEFAULT_AUTOCOMMIT_CHUNK = adaptFromMemory(50000L);

    /**
     * The default {@link Hasher} used to create unique identifiers.
     */
    private static final Hasher DEFAULT_HASHER = HasherFactory.md5();

    /**
     * The persistence back-end where to store data.
     */
    protected final PersistenceBackend backend;

    /**
     * Queue holding the current {@link Id} chain (current element and its parent).
     * <p>
     * It is updated after each addition/deletion of an element.
     */
    protected final Deque<Id> idsStack = new ArrayDeque<>();

    /**
     * In-memory cache that holds the recently processed {@link Id}s, identified by their literal representation.
     */
    private final Cache<String, Id> idsCache = Caffeine.newBuilder()
            .initialCapacity((int) DEFAULT_CACHE_SIZE / 10)
            .maximumSize(DEFAULT_CACHE_SIZE)
            .build();

    /**
     * Current number of modifications modulo {@link #DEFAULT_AUTOCOMMIT_CHUNK}.
     * Used for automatically saves modifications as calls are made.
     */
    private long autocommitCount;

    /**
     * Constructs a new {@code AbstractPersistenceWriter} on top of the {@code backend}.
     *
     * @param backend the persistence back-end where to store data
     */
    protected AbstractPersistenceWriter(PersistenceBackend backend) {
        this.backend = checkNotNull(backend);

        this.autocommitCount = 0;

        NeoLogger.info("{0} created", getClass().getSimpleName());
        NeoLogger.info("{0} chunk = {1}", getClass().getSimpleName(), DEFAULT_AUTOCOMMIT_CHUNK);
    }

    /**
     * Adapts the given {@code value} according to the maximum memory dedicated to the JVM.
     *
     * @param value the value to adapt
     *
     * @return the adapted value
     *
     * @note The formulas can be improved, for sure.
     * @see #DEFAULT_CACHE_SIZE
     * @see #DEFAULT_AUTOCOMMIT_CHUNK
     */
    private static long adaptFromMemory(long value) {
        checkArgument(value >= 0);

        long maxMemoryGB = Runtime.getRuntime().maxMemory() / 1000 / 1000 / 1000;

        long factor = maxMemoryGB;
        if (maxMemoryGB > 1) {
            factor *= 2;
        }

        return value * factor;
    }

    @Override
    public void onInitialize() {
        // Create the 'ROOT' node with the default metaclass
        RawMetaclass metaClass = RawMetaclass.getDefault();

        RawElement rootElement = new RawElement(metaClass.ns(), metaClass.name());
        rootElement.id(RawId.generated(PersistenceConstants.ROOT_ID.toString()));
        rootElement.className(metaClass.name());
        rootElement.isRoot(false);
        rootElement.metaclass(metaClass);

        createElement(rootElement, PersistenceConstants.ROOT_ID);
    }

    @Override
    public void onStartElement(RawElement element) {
        idsStack.addLast(createElement(element));
    }

    @Override
    public void onAttribute(RawAttribute attribute) {
        Id id = Optional.ofNullable(attribute.id())
                .map(this::getOrCreateId)
                .orElse(idsStack.getLast());

        addAttribute(id, attribute);
    }

    @Override
    public void onReference(RawReference reference) {
        Id id = Optional.ofNullable(reference.id())
                .map(this::getOrCreateId)
                .orElse(idsStack.getLast());

        Id idReference = getOrCreateId(reference.idReference());

        addReference(id, reference, idReference);
    }

    @Override
    public void onCharacters(String characters) {
        // Do nothing
    }

    @Override
    public void onEndElement() {
        idsStack.removeLast();
    }

    @Override
    public void onComplete() {
        backend.save();
    }

    /**
     * Creates an element from the given {@code element} with the given {@code id}.
     *
     * @param element the information about the new element
     * @param id      the identifier of the element
     *
     * @return the given {@code id}
     *
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    protected Id createElement(RawElement element, Id id) {
        checkNotNull(element);
        checkNotNull(id);

        persist(id);
        updateInstanceOf(id, element.metaclass().name(), element.metaclass().ns().uri());

        if (nonNull(element.className())) {
            RawAttribute attribute = new RawAttribute(PersistenceConstants.FEATURE_NAME);
            attribute.value(element.className());

            addAttribute(id, attribute);
        }

        // Add the current element as content of the 'ROOT' node
        if (element.isRoot()) {
            RawReference reference = new RawReference(PersistenceConstants.ROOT_FEATURE_NAME);
            reference.isMany(true);

            addReference(PersistenceConstants.ROOT_ID, reference, id);
        }

        incrementAndCommit();

        return id;
    }

    /**
     * Creates an element from the given {@code element}, and creates its {@link Id}.
     *
     * @param element the information about the new element
     *
     * @return the {@link Id} of the created element
     *
     * @throws NullPointerException if the {@code element} is {@code null} or if it does not have an {@link Id}
     */
    protected Id createElement(RawElement element) {
        checkNotNull(element.id());

        Id id = createId(element.id());

        createElement(element, id);
        idsCache.put(element.id().value(), id);

        return id;
    }

    /**
     * Returns the {@link Id} of the given {@code identifier}.
     *
     * @param identifier the representation of the {@link Id}
     *
     * @return the registered {@link Id} of the given identifier, or {@code null} if the identifier is not registered.
     */
    protected Id getOrCreateId(RawId identifier) {
        checkNotNull(identifier);

        return idsCache.get(identifier.value(), value -> createId(identifier));
    }

    /**
     * Creates an {@link Id} from the given {@code identifier}.
     *
     * @param identifier the representation of the {@link Id}
     *
     * @return the {@link Id}
     */
    protected Id createId(RawId identifier) {
        checkNotNull(identifier);

        String idValue = identifier.value();

        // If identifier has been generated we hash it, otherwise we use the original
        if (identifier.isGenerated()) {
            idValue = DEFAULT_HASHER.hash(idValue).toString();
        }

        return StringId.of(idValue);
    }

    /**
     * Adds a new {@code attribute} to the element identified by the given {@code id}.
     *
     * @param id        the identifier of the element
     * @param attribute the attribute to add
     */
    protected void addAttribute(Id id, RawAttribute attribute) {
        checkNotNull(id);
        checkNotNull(attribute);

        checkExists(id);

        FeatureKey key = FeatureKey.of(id, attribute.name());

        if (!attribute.isMany()) {
            backend.valueFor(key, attribute.value());
        }
        else {
            int index = attribute.index();
            if (index == -1) {
                backend.appendValue(key, attribute.value());
            }
            else {
                backend.addValue(key.withPosition(index), attribute.value());
            }
        }

        incrementAndCommit();
    }

    /**
     * Adds a new reference to the element identified by the given {@code id}.
     *
     * @param id          the identifier of the element
     * @param reference   the reference to add
     * @param idReference the identifier of the referenced element
     */
    protected void addReference(Id id, RawReference reference, Id idReference) {
        checkNotNull(id);
        checkNotNull(reference);
        checkNotNull(idReference);

        checkExists(id);
        checkExists(idReference);

        // Update the containment reference if needed
        if (reference.isContainment()) {
            updateContainment(id, reference.name(), idReference);
        }

        FeatureKey key = FeatureKey.of(id, reference.name());

        if (!reference.isMany()) {
            backend.referenceFor(key, idReference);
        }
        else {
            int index = reference.index();
            if (index == -1) {
                backend.appendReference(key, idReference);
            }
            else {
                backend.addReference(key.withPosition(index), idReference);
            }
        }

        incrementAndCommit();
    }

    /**
     * Updates the containment identified by its {@code name} between the {@code idContainer} and the {@code
     * idContainment}.
     *
     * @param idContainer   the identifier of the element
     * @param name          the name of the property identifying the reference (parent -&gt; child)
     * @param idContainment the identifier of the referenced element
     */
    protected void updateContainment(Id idContainer, String name, Id idContainment) {
        checkNotNull(idContainer);
        checkNotNull(name);
        checkNotNull(idContainment);

        Optional<ContainerDescriptor> container = backend.containerOf(idContainment);
        if (!container.isPresent() || !Objects.equals(container.get().id(), idContainer)) {
            backend.containerFor(idContainment, ContainerDescriptor.of(idContainer, name));
        }

        incrementAndCommit();
    }

    /**
     * Defines the metaclass to the element identified by the given {@code id}.
     *
     * @param id   the identifier of the element
     * @param name the name of the metaclass
     * @param uri  the uri of the metaclass
     */
    protected void updateInstanceOf(Id id, String name, String uri) {
        checkNotNull(id);
        checkNotNull(name);
        checkNotNull(uri);

        Optional<MetaclassDescriptor> metaclass = backend.metaclassOf(id);
        if (!metaclass.isPresent()) {
            backend.metaclassFor(id, MetaclassDescriptor.of(name, uri));
        }
        else {
            throw new IllegalArgumentException("An element with the same Id (" + id + ") is already defined. Use a handler with a conflicts resolution feature instead.");
        }

        incrementAndCommit();
    }

    /**
     * Increments the operation counter, and commit the persistence back-end if the number of operation is equals to
     * {@code OPS_BETWEEN_COMMITS_DEFAULT}.
     */
    private void incrementAndCommit() {
        autocommitCount = (autocommitCount + 1) % DEFAULT_AUTOCOMMIT_CHUNK;
        if (autocommitCount == 0) {
            backend.save();
        }
    }

    /**
     * Creates a new element identified by the specified {@code id}.
     *
     * @param id the identifier of the element
     *
     * @throws AlreadyExistingIdException if the {@code id} is already used as primary key for another element
     */
    protected abstract void persist(Id id);

    /**
     * Checks whether the {@code id} is already existing in the back-end.
     *
     * @param id the identifier of the element
     *
     * @throws NoSuchElementException if no element is found with the {@code id}
     */
    protected abstract void checkExists(Id id);
}
