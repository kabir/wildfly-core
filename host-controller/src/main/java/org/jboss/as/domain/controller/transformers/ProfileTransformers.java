/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.domain.controller.transformers;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition;

/**
 * The older versions of the model do not allow {@code null} for the system property boottime attribute.
 * If it is {@code null}, make sure it is {@code true}
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ProfileTransformers {

    static ChainedTransformationDescriptionBuilder  buildTransformerChain(ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(SystemPropertyResourceDefinition.PATH, currentVersion);

        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(currentVersion, DomainTransformers.VERSION_2_1);
        internalRegisterTransformersBeforeIncludes(builder);

        builder = chainedBuilder.createBuilder(currentVersion, DomainTransformers.VERSION_1_6);
        internalRegisterTransformersBeforeIncludes(builder);

        return chainedBuilder;
    }

    private static void internalRegisterTransformersBeforeIncludes(ResourceTransformationDescriptionBuilder builder) {
        builder.getAttributeBuilder()
            .addRejectCheck(RejectAttributeChecker.DEFINED, ProfileResourceDefinition.INCLUDES)
            .setDiscard(DiscardAttributeChecker.UNDEFINED, ProfileResourceDefinition.INCLUDES)
            .end();
    }

}
