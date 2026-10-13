package xyz.rayniyomi.cloudstream;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

import eu.kanade.tachiyomi.animesource.AnimeSource;
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory;
import uy.kohesive.injekt.InjektKt;

/**
 * The host loads this class child-first from the runtime APK, which also bundles Kotlin for the
 * manager app. It is Java so that it pulls no Kotlin class into the host. It loads the real runtime
 * from assets/host-runtime.jar through a loader whose shared classes come from the host.
 */
public final class Entry implements AnimeSourceFactory {

    private static final String TAG = "CloudStreamRuntime";
    private static final String RUNTIME_JAR = "assets/host-runtime.jar";
    private static final String RUNTIME_FACTORY = "xyz.rayniyomi.cloudstream.host.RuntimeFactory";

    @Override
    public List<AnimeSource> createSources() {
        try {
            return load();
        } catch (Throwable error) {
            Log.e(TAG, "CloudStream runtime failed to load", error);
            return Collections.emptyList();
        }
    }

    private List<AnimeSource> load() throws Exception {
        Application application = InjektKt.getInjekt().getInstance(Application.class);
        File jar = extractRuntime(new File(application.getCodeCacheDir(), "cloudstream-runtime"));
        ClassLoader loader = new AllowlistDexClassLoader(jar.getPath(), AnimeSourceFactory.class.getClassLoader());
        AnimeSourceFactory factory = (AnimeSourceFactory) loader.loadClass(RUNTIME_FACTORY)
            .getConstructor(String.class)
            .newInstance(BuildConfig.APPLICATION_ID);
        return factory.createSources();
    }

    private File extractRuntime(File directory) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create " + directory);
        }
        File temp = File.createTempFile("host-runtime", ".tmp", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = openRuntimeJar(); OutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            File target = new File(directory, "host-runtime-" + hex(digest.digest()).substring(0, 16) + ".jar");
            if (!target.isFile()) {
                if (!temp.setReadOnly() || !temp.renameTo(target) && !target.isFile()) {
                    throw new IllegalStateException("Cannot store " + target);
                }
            }
            deleteOtherJars(directory, target);
            return target;
        } finally {
            temp.delete();
        }
    }

    private static InputStream openRuntimeJar() {
        InputStream input = Entry.class.getClassLoader().getResourceAsStream(RUNTIME_JAR);
        if (input == null) {
            throw new IllegalStateException("The runtime APK has no " + RUNTIME_JAR);
        }
        return input;
    }

    private static void deleteOtherJars(File directory, File keep) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.equals(keep) && file.getName().endsWith(".jar")) {
                file.delete();
            }
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
