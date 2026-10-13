package xyz.rayniyomi.cloudstream;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexClassLoader;

/** Shared API classes must use the host definitions, while plugin libraries must retain their own versions. */
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
