/*
 * Copyright (c) 2013-2018 Atlanmod, Inria, LS2N, and IMT Nantes.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 */

package fr.inria.atlanmod.neoemf.io.listener;

import fr.inria.atlanmod.commons.log.Log;
import fr.inria.atlanmod.neoemf.io.bean.BasicAttribute;
import fr.inria.atlanmod.neoemf.io.bean.BasicElement;
import fr.inria.atlanmod.neoemf.io.bean.BasicReference;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnegative;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A {@link EventListener} that counts the number of different element.
 */
@ParametersAreNonnullByDefault
public class CountingEventListener extends AbstractEventListener {

    /**
     * The current number of element.
     */
    @Nonnegative
    private final AtomicLong elementCount = new AtomicLong();

    /**
     * The current number of attribute.
     */
    @Nonnegative
    private final AtomicLong attributeCount = new AtomicLong();

    /**
     * The current number of reference.
     */
    @Nonnegative
    private final AtomicLong referenceCount = new AtomicLong();

    @Override
    public void onStartElement(BasicElement element) {
        elementCount.incrementAndGet();
    }

    @Override
    public void onAttribute(BasicAttribute attribute) {
        attributeCount.incrementAndGet();
    }

    @Override
    public void onReference(BasicReference reference) {
        referenceCount.incrementAndGet();
    }

    @Override
    public void onComplete() {
        Log.info("Elements   : {0,number,#}", elementCount);
        Log.info("Attributes : {0,number,#}", attributeCount);
        Log.info("References : {0,number,#}", referenceCount);
    }
}
