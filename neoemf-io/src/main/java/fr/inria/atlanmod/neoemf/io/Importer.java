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

package fr.inria.atlanmod.neoemf.io;

import fr.inria.atlanmod.neoemf.io.persistence.PersistenceHandler;
import fr.inria.atlanmod.neoemf.io.processor.Processor;
import fr.inria.atlanmod.neoemf.io.reader.Reader;
import fr.inria.atlanmod.neoemf.io.reader.xmi.XmiStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The import factory.
 */
public class Importer {

    private Importer() {
    }

    /**
     * Imports a XMI file into persistence handlers.
     *
     * @param stream              the stream of XMI data
     * @param persistenceHandlers persistence handlers where to store the read data
     *
     * @throws IllegalArgumentException if there is no handler to notify
     * @throws IOException if an error occurred during the import
     */
    public static void fromXmi(InputStream stream, PersistenceHandler... persistenceHandlers) throws IOException {
        checkArgument(persistenceHandlers.length > 0);

        Reader reader = new XmiStreamReader();
        Processor processor = reader.defaultProcessor();

        Arrays.stream(persistenceHandlers).forEach(processor::addHandler);
        reader.addHandler(processor);

        reader.read(stream);
    }
}