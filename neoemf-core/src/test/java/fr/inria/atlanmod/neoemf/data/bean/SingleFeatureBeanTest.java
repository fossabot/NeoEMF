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

package fr.inria.atlanmod.neoemf.data.bean;

import fr.inria.atlanmod.commons.AbstractTest;
import fr.inria.atlanmod.neoemf.core.Id;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test-case about {@link SingleFeatureBean}.
 */
public class SingleFeatureBeanTest extends AbstractTest {

    /**
     * Checks the comparison of 2 {@link SingleFeatureBean}s. In this case, they are equal.
     */
    @Test
    public void testCompareEqualTo() {
        Id id0 = Id.getProvider().fromLong(42);

        SingleFeatureBean key0 = SingleFeatureBean.of(id0, "Feature0");
        SingleFeatureBean key1 = SingleFeatureBean.of(id0, "Feature0");

        assertThat(key0).isEqualByComparingTo(key1);
    }

    /**
     * Checks the comparison of 2 {@link SingleFeatureBean}s. In this case, the first is lower than the second.
     */
    @Test
    public void testCompareLowerThan() {
        Id id0 = Id.getProvider().fromLong(42);

        SingleFeatureBean key0 = SingleFeatureBean.of(id0, "Feature0");
        SingleFeatureBean key1 = SingleFeatureBean.of(id0, "Feature1");

        assertThat(key0).isLessThan(key1);
    }

    /**
     * Checks the comparison of 2 {@link SingleFeatureBean}s. In this case, the first is greater than the second.
     */
    @Test
    public void testCompareGreaterThan() {
        SingleFeatureBean key0 = SingleFeatureBean.of(Id.getProvider().fromLong(44), "Feature0");
        SingleFeatureBean key1 = SingleFeatureBean.of(Id.getProvider().fromLong(42), "Feature1");

        assertThat(key0).isGreaterThan(key1);
    }
}
