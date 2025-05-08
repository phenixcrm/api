package net.inetalliance.potion.obj;

import com.ameriglide.phenix.core.Log;
import lombok.Getter;
import lombok.val;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.annotations.MaxLength;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.www.ContentType;
import net.inetalliance.util.security.Md5InputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public abstract class ExternalFile {

    private static final Log log = new Log();
    private final transient Object container;
    private final transient String name;
    @Getter
    @MaxLength(32)
    private String md5;

    protected ExternalFile(final Object container, final String name) {
        super();
        this.container = container;
        this.name = name;
    }

    public File set(final File repository, final File data, final ContentType contentType)
            throws IOException, NoSuchAlgorithmException {
        log.debug(() -> "ExternalFile.set(%s, %s, %s)".formatted(repository.getAbsolutePath(),
                data.getAbsolutePath(),
                contentType.value));
        delete(repository);
        val src = new Md5InputStream(new FileInputStream(data));
        val file = new File(repository, getPath("", contentType));
        if (!file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) {
                log.warn(() -> "Could not create directory %s".formatted(file.getParentFile().getAbsolutePath()));
            }
        }
        Files.copy(src, file.toPath(), REPLACE_EXISTING);
        src.close();
        log.debug(() -> "ExternalFile.set copied %s -> %s".formatted(data.getAbsolutePath(), file.getAbsolutePath()));
        md5 = src.getMd5();
        return file;
    }

    public boolean delete(final File repository)
            throws IOException {
        var found = false;
        if (hasData()) {
            for (val file : getFiles(repository.getParentFile())) {
                if (file.exists()) {
                    if (!file.delete()) {
                        log.warn(() -> "Could not delete %s".formatted(file.getAbsolutePath()));
                    }
                    this.md5 = null;
                    found = true;
                }
            }
        }
        return found;
    }

    protected String getPath(final String prefix, final ContentType contentType) {
        return String.format("%s/%s/%s/%s.%s", prefix, container.getClass().getSimpleName(),
                Info.keysToString(container),
                name, contentType.extension);
    }

    public boolean hasData() {
        return getContentType() != null;
    }

    public Collection<File> getFiles(final File root) {
        return hasData() ? Collections.singleton(new File(root, getPath()))
                : Collections.emptySet();
    }

    public abstract ContentType getContentType();

    public String getPath() {
        return getPath(false);
    }

    public String getPath(final boolean noNonce) {
        return getPath("/static", getContentType()) + (noNonce ? "" : "?nonce=" + md5);
    }

    public JsonMap toJson() {
        return new JsonMap().$("path", getPath());
    }
}
