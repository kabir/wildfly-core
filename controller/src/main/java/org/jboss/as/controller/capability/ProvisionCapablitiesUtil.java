/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.capability;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProvisionCapablitiesUtil {

    private static final String ALLOW_MULTIPLE_REGISTRATIONS = "allow-multiple-registrations";
    private static final String NON_MODULAR = "non-modular";
    private static final String REQUIREMENTS = "requirements";
    private static final String REGISTRATION_POINTS = "registration-points";
    private static final String RUNTIME_API = "runtime-api";
    private static final String SCOPE = "scope";
    private static final String SERVICE_VALUE_TYPE = "service-value-type";

    public static ModelNode provisionCapabilities(final CapabilityRegistry capabilityRegistry) {
        final ModelNode result = new ModelNode();
        Set<CapabilityRegistration<?>> caps = capabilityRegistry.getCapabilities();
        for (CapabilityRegistration cr : caps) {
            ModelNode cap = result.add();
            cap.get(NAME).set(cr.getCapabilityName());
            cap.get(SCOPE).set(cr.getCapabilityScope().getName());
            Capability capability = cr.getCapability();
            outputSetIfExists(cap, REQUIREMENTS, capability.getRequirements());
            if (capability instanceof RuntimeCapability) {
                RuntimeCapability rc = (RuntimeCapability<?>)capability;
                cap.get(ALLOW_MULTIPLE_REGISTRATIONS).set(rc.isAllowMultipleRegistrations());

                Class<?> serviceValueType = rc.getCapabilityServiceValueType();
                if (serviceValueType != null) {
                    cap.get(SERVICE_VALUE_TYPE).set(classInformation(serviceValueType));
                }

                Object o = rc.getRuntimeAPI();
                if (o != null) {
                    ModelNode classInfo = classInformation(o.getClass());
                    try {
                        classInfo.get(BYTES).set(serializeToHex(o));
                    } catch (IOException e) {
                        throw ControllerLogger.ROOT_LOGGER.errorSerializingRuntimeApiForCapability(cr.getCapabilityName());
                    }
                    cap.get(RUNTIME_API).set(classInfo);
                    // TODO the actual serialization
                }
            }

            ModelNode regPoints = new ModelNode().setEmptyList();
            Set<RegistrationPoint> rps = cr.getRegistrationPoints();
            synchronized (rps){
                for (RegistrationPoint rp : rps) {
                    regPoints.add(rp.getAddress().toCLIStyleString());
                }
            }
            cap.get(REGISTRATION_POINTS).set(regPoints);

            // Do we need this?
            //populateRegistrationPoints(cap.get("registration-points"), cr.getRegistrationPoints());
        }
        return result;
    }

    private static String serializeToHex(Object o) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bout);) {
            out.writeObject(o);
        }
        return bytesToHex(bout.toByteArray());
    }

    public static void installProvisionedCapabilities(final CapabilityRegistry capabilityRegistry, final ModelNode info) throws ModuleLoadException, ClassNotFoundException, IOException {
        for (ModelNode capInfo : info.asList()) {
            installProvisionedCapability(capabilityRegistry, capInfo);
        }
    }

    private static void installProvisionedCapability(final CapabilityRegistry capabilityRegistry, final ModelNode capInfo) throws ModuleLoadException, ClassNotFoundException, IOException {
        final String name = capInfo.get(NAME).asString();
        final ModelNode runtimeApi = capInfo.hasDefined(RUNTIME_API) ? capInfo.get(RUNTIME_API) : new ModelNode();

        final RuntimeCapability.Builder<?> builder;
        if (runtimeApi.isDefined()) {
            Object o = deserializeRuntimeApi(runtimeApi);
            builder = RuntimeCapability.Builder.of(name, o);
        } else {
            builder = RuntimeCapability.Builder.of(name);
        }

        builder.setAllowMultipleRegistrations(capInfo.get(ALLOW_MULTIPLE_REGISTRATIONS).asBoolean());

        final ModelNode serviceValueType = capInfo.hasDefined(SERVICE_VALUE_TYPE) ? capInfo.get(SERVICE_VALUE_TYPE) : new ModelNode();
        if (serviceValueType.isDefined()) {
            builder.setServiceType(loadClass(serviceValueType));
        }

        if (capInfo.hasDefined(REQUIREMENTS)) {
            builder.addRequirements(toArray(capInfo.get(REQUIREMENTS)));
        }

        final RuntimeCapability<?> runtimeCapability = builder.build();

        final String regPointAddr = capInfo.get(REGISTRATION_POINTS).asList().iterator().next().asString();
        final PathAddress addr = regPointAddr.equals("/") ?
                PathAddress.EMPTY_ADDRESS
                : PathAddress.parseCLIStyleAddress(regPointAddr);
        final RegistrationPoint registrationPoint = new RegistrationPoint(addr, null);

        final String scope = capInfo.get(SCOPE).asString();
        final CapabilityScope capabilityScope;
        if (scope.equals(CapabilityScope.GLOBAL.getName())) {
            capabilityScope = CapabilityScope.GLOBAL;
        } else {
            throw ControllerLogger.ROOT_LOGGER.unknownProvisionedCapabilityScope(scope, name);
        }

        RuntimeCapabilityRegistration reg = new RuntimeCapabilityRegistration(runtimeCapability, capabilityScope, registrationPoint);
        capabilityRegistry.registerCapability(reg);
    }

    private static Object deserializeRuntimeApi(ModelNode runtimeApi) throws ModuleLoadException, ClassNotFoundException, IOException {
        Class<?> clazz = loadClass(runtimeApi);
        ByteArrayInputStream bin = new ByteArrayInputStream(hexToBytes(runtimeApi.get(BYTES).asString()));
        try (ObjectInputStream in = new ObjectInputStream(bin) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    return clazz;
                }
            }) {
            return in.readObject();
        }
    }

    private static Class<?> loadClass(ModelNode classInfo) throws ClassNotFoundException, ModuleLoadException {
        final String moduleName = classInfo.get(MODULE).asString();
        final String name = classInfo.get(NAME).asString();

        ClassLoader myClassLoader = WildFlySecurityManager.getClassLoaderPrivileged(ProvisionCapablitiesUtil.class);

        if (!(myClassLoader instanceof ModuleClassLoader) || moduleName.equals(NON_MODULAR)) {
            // Be able to run in unit tests in a non-modular environment
            return myClassLoader.loadClass(name);
        }
        Module module = ((ModuleClassLoader) myClassLoader).getModule().getModuleLoader().loadModule(moduleName);
        if (WildFlySecurityManager.isChecking()) {
            try {
                return WildFlySecurityManager.doChecked(new PrivilegedExceptionAction<Class<?>>() {
                    @Override
                    public Class<?> run() throws Exception {
                        return module.getClassLoader().loadClass(name);
                    }
                });
            } catch (PrivilegedActionException e) {
                Exception err = e.getException();
                if (err instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException)err;
                }
                throw new RuntimeException(err);
            }

        } else {
            return module.getClassLoader().loadClass(name);
        }

    }

    private static void outputSetIfExists(final ModelNode output, final String key, final Set<String> reqs) {
        if (reqs.size() > 0) {
            output.get(key).set(toList(reqs));
        }
    }

    private static ModelNode toList(final Collection<String> collection) {
        final ModelNode n = new ModelNode().setEmptyList();
        for (String s : collection) {
            n.add(s);
        }
        return n;
    }

    private static String[] toArray(ModelNode list) {
        final List<ModelNode> nodeList = list.asList();
        final String[] arr = new String[nodeList.size()];

        for (int i = 0 ; i < nodeList.size() ; i++) {
            arr[i] = nodeList.get(i).asString();
        }
        return arr;
    }

    private static ModelNode classInformation(final Class<?> clazz) {
        final ModelNode info = new ModelNode();
        final ClassLoader loader = WildFlySecurityManager.getClassLoaderPrivileged(clazz);
        final String module = loader instanceof ModuleClassLoader ?
                ((ModuleClassLoader)loader).getModule().getName()
                : NON_MODULAR; // No need for i18n, real users will be in a modular environment
        info.get(MODULE).set(module);
        info.get(NAME).set(clazz.getName());
        return info;
    }


    private static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] hexToBytes(String s) {
        byte[] data = new byte[s.length() / 2];
        for (int i = 0 ; i < s.length() ; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static void main(String[] args) {
        byte[] input = "Test".getBytes();
        String hex = bytesToHex(input);
        System.out.println(hex);
    }
}
