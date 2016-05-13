/*******************************************************************************
 * Copyright (c) 2013 Atlanmod INRIA LINA Mines Nantes
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlanmod INRIA LINA Mines Nantes - initial API and implementation
 *******************************************************************************/

package fr.inria.atlanmod.neoemf.datastore.estores;

import fr.inria.atlanmod.neoemf.core.Id;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.InternalEObject.EStore;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * This interface extends the {@link EStore} interface and allows to establish a
 * mapping between {@link Resource}s and {@link EStore}s
 * 
 */
public interface SearcheableResourceEStore extends InternalEObject.EStore {

	/**
	 * Returns the {@link Resource} to which this {@link EStore} is associated
	 * 
	 * @return
	 */
	Resource resource();

	/**
	 * Returns the resolved {@link EObject} identified by the given
	 * {@code id} or {@code null} if no {@link EObject} can be resolved.
	 * 
	 * @param id
	 * @return
	 */
	EObject eObject(Id id);
	
	/**
	 * Back-end specific computation of allInstances()
	 * @param eClass the EClass to compute the instances of
	 * @param strict true if the lookup searches for strict instances
	 * @return an {@link EList} containing all the EObjects that are instances of the given {@link EClass}
	 * @throws UnsupportedOperationException if the back-end does not support custom all instances computation
	 */
	EList<EObject> getAllInstances(EClass eClass, boolean strict) throws UnsupportedOperationException;
	
}
