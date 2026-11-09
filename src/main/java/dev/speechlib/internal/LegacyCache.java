package dev.speechlib.internal;

import java.nio.file.*;
import java.io.IOException;
import com.google.gson.JsonObject;

/** Read-only reuse of checksum-verified SayItem assets during migration. */
final class LegacyCache {
    private LegacyCache(){}
    static Path source(Path target) {
        Path absolute=target.toAbsolutePath().normalize();
        for(Path parent=absolute.getParent();parent!=null;parent=parent.getParent())
            if(parent.getFileName()!=null && parent.getFileName().toString().equals(".speechlib") && parent.getParent()!=null)
                return parent.getParent().resolve(".sayitem/speech").resolve(parent.relativize(absolute));
        return null;
    }
    static void copyRuntime(Path root,JsonObject files)throws IOException {
        for(var entry:files.entrySet()) {
            Path target=root.resolve(entry.getKey()).normalize(),source=source(target);
            if(!target.startsWith(root) || source==null || !Files.isRegularFile(source))return;
            if(!BundledFiles.sha256(source).equals(entry.getValue().getAsString()))return;
        }
        for(var entry:files.entrySet()) {
            Path target=root.resolve(entry.getKey()).normalize();Files.createDirectories(target.getParent());
            Files.copy(source(target),target,StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
