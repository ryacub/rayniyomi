package xyz.rayniyomi.cloudstream;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexClassLoader;

/**
 * Loads the CloudStream runtime jar inside the host process.
 * Classes that cross the source API, or that the host must own, come only from the host.
 * All other classes come from the jar first, so the host's versions of those libraries do not apply.
 */
final class AllowlistDexClassLoader extends DexClassLoader {

    AllowlistDexClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, null, null, parent);
    }

    static boolean isShared(String name) {
        for (String prefix : SharedPrefixes.VALUES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (isShared(name)) {
                    loaded = getParent().loadClass(name);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notInJar) {
                        loaded = getParent().loadClass(name);
                    }
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    public URL getResource(String name) {
        URL resource = findResource(name);
        return resource != null ? resource : getParent().getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> resources = new ArrayList<>(Collections.list(findResources(name)));
        resources.addAll(Collections.list(getParent().getResources(name)));
        return Collections.enumeration(resources);
    }
}
