package org.joget.marketplace;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(UninstallPluginAlert.class.getName(), new UninstallPluginAlert(), null));
        registrationList.add(context.registerService(ShowPluginUsage.class.getName(), new ShowPluginUsage(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }

    /**
     * Bundle-Version from the manifest (derived from pom.xml <version>) - single
     * source of truth for every plugin class's getVersion(), instead of each one
     * hardcoding its own copy of the version string.
     */
    public static String getBundleVersion() {
        Bundle bundle = FrameworkUtil.getBundle(Activator.class);
        return bundle != null ? bundle.getVersion().toString() : "";
    }
}