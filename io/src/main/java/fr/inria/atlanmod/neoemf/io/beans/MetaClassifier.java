/*
 * Copyright (c) 2013 Atlanmod INRIA LINA Mines Nantes.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlanmod INRIA LINA Mines Nantes - initial API and implementation
 */

package fr.inria.atlanmod.neoemf.io.beans;

/**
 * A simple element identified by a {@link Namespace namespace} and its name inside it.
 */
public class MetaClassifier extends NamedElement {

    private static final MetaClassifier DEFAULT = new MetaClassifier(Namespace.getDefault(), "EObject");

    public static MetaClassifier getDefault() {
        return DEFAULT;
    }

    private Namespace namespace;

    public MetaClassifier(Namespace namespace, String localName) {
        super(localName);
        this.namespace = namespace;
    }

    public Namespace getNamespace() {
        return namespace;
    }

    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    @Override
    public String toString() {
        return namespace.getPrefix() + ':' + getLocalName();
    }
}