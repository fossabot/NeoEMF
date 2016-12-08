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

package fr.inria.atlanmod.neoemf.graph.blueprints.datastore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Iterables;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Index;
import com.tinkerpop.blueprints.KeyIndexableGraph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.GraphHelper;
import com.tinkerpop.blueprints.util.wrappers.id.IdEdge;
import com.tinkerpop.blueprints.util.wrappers.id.IdGraph;

import fr.inria.atlanmod.neoemf.core.Id;
import fr.inria.atlanmod.neoemf.core.PersistenceFactory;
import fr.inria.atlanmod.neoemf.core.PersistentEObject;
import fr.inria.atlanmod.neoemf.core.StringId;
import fr.inria.atlanmod.neoemf.datastore.AbstractPersistenceBackend;
import fr.inria.atlanmod.neoemf.logging.NeoLogger;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EPackageImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class BlueprintsPersistenceBackend extends AbstractPersistenceBackend {

    /**
     * The literal description of this backend.
     */
    public static final String NAME = "blueprints";

    public static final String KEY_ECLASS_NAME = EcorePackage.eINSTANCE.getENamedElement_Name().getName();
    public static final String KEY_EPACKAGE_NSURI = EcorePackage.eINSTANCE.getEPackage_NsURI().getName();
    public static final String KEY_INSTANCE_OF = "kyanosInstanceOf";
    public static final String KEY_METACLASSES = "metaclasses";
    public static final String KEY_NAME = "name";

    // TODO Find the more predictable maximum cache size
    private static final int DEFAULT_CACHE_SIZE = 10000;

    private final Cache<Id, PersistentEObject> persistentObjectsCache;
    private final Cache<Id, Vertex> verticesCache;
    private final List<EClass> indexedEClasses;

    private final IdGraph<KeyIndexableGraph> graph;

    private Index<Vertex> metaclassIndex;
    private boolean isClosed = false;

    BlueprintsPersistenceBackend(KeyIndexableGraph baseGraph) {
        this.graph = new AutoCleanerIdGraph(baseGraph);
        this.persistentObjectsCache = Caffeine.newBuilder().maximumSize(DEFAULT_CACHE_SIZE).softValues().build();
        this.verticesCache = Caffeine.newBuilder().maximumSize(DEFAULT_CACHE_SIZE).softValues().build();
        this.indexedEClasses = new ArrayList<>();
        this.metaclassIndex = graph.getIndex(KEY_METACLASSES, Vertex.class);
        if (isNull(metaclassIndex)) {
            metaclassIndex = graph.createIndex(KEY_METACLASSES, Vertex.class);
        }
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void close() {
        try {
            graph.shutdown();
        }
        catch (Exception e) {
            NeoLogger.warn(e);
        }
        isClosed = true;
    }

    @Override
    public void save() {
        if (graph.getFeatures().supportsTransactions) {
            graph.commit();
        }
        else {
            graph.shutdown();
        }
    }

    @Override
    public Map<EClass, Iterable<Vertex>> getAllInstances(EClass eClass, boolean strict) {
        Map<EClass, Iterable<Vertex>> indexHits;

        // There is no strict instance of an abstract class
        if (eClass.isAbstract() && strict) {
            indexHits = Collections.emptyMap();
        }
        else {
            indexHits = new HashMap<>();
            Set<EClass> eClassToFind = new HashSet<>();
            eClassToFind.add(eClass);

            // Find all the concrete subclasses of the given EClass (the metaclass index only stores concretes EClass)
            if (!strict) {
                eClass.getEPackage().getEClassifiers()
                        .stream()
                        .filter(EClass.class::isInstance)
                        .map(EClass.class::cast)
                        .filter(c -> eClass.isSuperTypeOf(c) && !c.isAbstract())
                        .forEach(eClassToFind::add);
            }
            // Get all the vertices that are indexed with one of the EClass
            for (EClass ec : eClassToFind) {
                Vertex metaClassVertex = Iterables.getOnlyElement(metaclassIndex.get(KEY_NAME, ec.getName()), null);
                if (nonNull(metaClassVertex)) {
                    Iterable<Vertex> instanceVertexIterable = metaClassVertex.getVertices(Direction.IN, KEY_INSTANCE_OF);
                    indexHits.put(ec, instanceVertexIterable);
                }
                else {
                    NeoLogger.warn("Metaclass {0} not found in index", ec.getName());
                }
            }
        }
        return indexHits;
    }

    /**
     * Builds the {@link Id} used to identify {@link EClass} {@link Vertex}es.
     */
    private static Id buildId(EClass eClass) {
        return isNull(eClass) ? null : new StringId(eClass.getName() + '@' + eClass.getEPackage().getNsURI());
    }

    /**
     * Create a new vertex, add it to the graph, and return the newly created vertex.
     *
     * @param id the identifier of the {@link Vertex}
     *
     * @return the newly created vertex
     */
    public Vertex addVertex(Id id) {
        return graph.addVertex(id.toString());
    }

    /**
     * Create a new vertex, add it to the graph, and return the newly created vertex. The issued {@link EClass} is used
     * to calculate the {@link Vertex} {@code id}.
     *
     * @param eClass The corresponding {@link EClass}
     *
     * @return the newly created vertex
     */
    private Vertex addVertex(EClass eClass) {
        Vertex vertex = addVertex(buildId(eClass));
        vertex.setProperty(KEY_ECLASS_NAME, eClass.getName());
        vertex.setProperty(KEY_EPACKAGE_NSURI, eClass.getEPackage().getNsURI());
        return vertex;
    }

    /**
     * Returns the vertex corresponding to the provided {@code id}. If no vertex corresponds to that {@code id}, then
     * return {@code null}.
     *
     * @return the vertex referenced by the provided {@link EObject} or {@code null} when no such vertex exists
     */
    public Vertex getVertex(Id id) {
        return verticesCache.get(id, key -> graph.getVertex(key.toString()));
    }

    /**
     * Returns the vertex corresponding to the provided {@link EClass}. If no vertex corresponds to that {@link EClass},
     * then return {@code null}.
     *
     * @return the vertex referenced by the provided {@link EClass} or {@code null} when no such vertex exists
     */
    private Vertex getVertex(EClass eClass) {
        return getVertex(buildId(eClass));
    }

    /**
     * Return the vertex corresponding to the provided {@link EObject}. If no vertex corresponds to that {@link EObject},
     * then the corresponding {@link Vertex} together with its {@link #KEY_INSTANCE_OF} relationship is created.
     *
     * @return the vertex referenced by the provided {@link EObject} or {@code null} when no such vertex exists
     */
    public Vertex getOrCreateVertex(PersistentEObject eObject) {
        Vertex vertex;
        if (eObject.isMapped()) {
            vertex = getVertex(eObject.id());
        }
        else {
            vertex = createVertex(eObject);
        }
        return vertex;
    }

    private void setMappedVertex(Vertex vertex, PersistentEObject object) {
        object.setMapped(true);
        persistentObjectsCache.put(object.id(), object);
        verticesCache.put(object.id(), vertex);
    }

    private EClass resolveInstanceOf(Vertex vertex) {
        EClass returnValue = null;
        Vertex eClassVertex = Iterables.getOnlyElement(vertex.getVertices(Direction.OUT, KEY_INSTANCE_OF), null);
        if (nonNull(eClassVertex)) {
            String name = eClassVertex.getProperty(KEY_ECLASS_NAME);
            String nsUri = eClassVertex.getProperty(KEY_EPACKAGE_NSURI);
            returnValue = (EClass) Registry.INSTANCE.getEPackage(nsUri).getEClassifier(name);
        }
        return returnValue;
    }

    /**
     * Reifies the given {@link Vertex} as an {@link EObject}.
     * <p/>
     * The method guarantees that the same {@link EObject} is returned for a given {@link Vertex} in subsequent calls,
     * unless the {@link EObject} returned in previous calls has been already garbage collected.
     */
    public PersistentEObject reifyVertex(Vertex vertex, EClass eClass) {
        PersistentEObject persistentEObject = null;

        Id id = new StringId(vertex.getId().toString());
        if (isNull(eClass)) {
            eClass = resolveInstanceOf(vertex);
        }
        try {
            persistentEObject = persistentObjectsCache.get(id, new PersistentEObjectCacheLoader(eClass));
        }
        catch (Exception e) {
            NeoLogger.error(e);
        }
        return persistentEObject;
    }

    private Vertex createVertex(PersistentEObject persistentEObject) {
        Vertex vertex = addVertex(persistentEObject.id());
        EClass eClass = persistentEObject.eClass();

        Vertex eClassVertex = Iterables.getOnlyElement(metaclassIndex.get(KEY_NAME, eClass.getName()), null);
        if (isNull(eClassVertex)) {
            eClassVertex = addVertex(eClass);
            metaclassIndex.put(KEY_NAME, eClass.getName(), eClassVertex);
            indexedEClasses.add(eClass);
        }
        vertex.addEdge(KEY_INSTANCE_OF, eClassVertex);
        setMappedVertex(vertex, persistentEObject);
        return vertex;
    }

    /**
     * Copies all the contents of this backend to the target one.
     */
    public void copyTo(BlueprintsPersistenceBackend target) {
        GraphHelper.copyGraph(graph, target.graph);
        target.initMetaClassesIndex(indexedEClasses);
    }

    private void initMetaClassesIndex(List<EClass> eClassList) {
        for (EClass eClass : eClassList) {
            checkArgument(Iterables.isEmpty(metaclassIndex.get(KEY_NAME, eClass.getName())), "Index is not consistent");
            metaclassIndex.put(KEY_NAME, eClass.getName(), getVertex(eClass));
        }
    }

    private static class PersistentEObjectCacheLoader implements Function<Id, PersistentEObject> {

        private final EClass eClass;

        private PersistentEObjectCacheLoader(EClass eClass) {
            this.eClass = eClass;
        }

        @Override
        public PersistentEObject apply(Id id) {
            PersistentEObject persistentEObject;
            if (nonNull(eClass)) {
                EObject eObject;
                if (Objects.equals(eClass.getEPackage().getClass(), EPackageImpl.class)) {
                    // Dynamic EMF
                    eObject = PersistenceFactory.getInstance().create(eClass);
                }
                else {
                    eObject = EcoreUtil.create(eClass);
                }
                persistentEObject = PersistentEObject.from(eObject);
                persistentEObject.id(id);
                persistentEObject.setMapped(true);
            }
            else {
                throw new RuntimeException("Element " + id + " does not have an associated EClass");
            }
            return persistentEObject;
        }
    }

    private static class AutoCleanerIdGraph extends IdGraph<KeyIndexableGraph> {

        public AutoCleanerIdGraph(KeyIndexableGraph baseGraph) {
            super(baseGraph);
        }

        @Override
        public Edge addEdge(Object id, Vertex outVertex, Vertex inVertex, String label) {
            return createFrom(super.addEdge(id, outVertex, inVertex, label));
        }

        @Override
        public Edge getEdge(Object id) {
            return createFrom(super.getEdge(id));
        }

        private Edge createFrom(Edge edge) {
            return isNull(edge) ? null : new AutoCleanerIdEdge(edge);
        }

        private class AutoCleanerIdEdge extends IdEdge {

            public AutoCleanerIdEdge(Edge edge) {
                super(edge, AutoCleanerIdGraph.this);
            }

            /**
             * {@inheritDoc}
             * <p/>
             * If the {@link Edge} references a {@link Vertex} with no more incoming
             * {@link Edge}, the referenced {@link Vertex} is removed as well.
             */
            @Override
            public void remove() {
                Vertex referencedVertex = getVertex(Direction.IN);
                super.remove();
                if (Iterables.isEmpty(referencedVertex.getEdges(Direction.IN))) {
                    // If the Vertex has no more incoming edges remove it from the DB
                    referencedVertex.remove();
                }
            }
        }
    }
}
