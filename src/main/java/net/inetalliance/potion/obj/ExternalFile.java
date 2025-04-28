package net.inetalliance.potion.obj;

import com.ameriglide.phenix.core.Log;
import net.inetalliance.potion.info.Info;
import net.inetalliance.types.annotations.MaxLength;
import net.inetalliance.types.json.JsonMap;
import net.inetalliance.types.util.FileUtil;
import net.inetalliance.types.www.ContentType;
import net.inetalliance.util.security.Md5InputStream;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;

public abstract class ExternalFile {

  private static final Log log = new Log();
  private final transient Object container;
  private final transient String name;
  @MaxLength(32)
  private String md5;

  protected ExternalFile(final Object container, final String name) {
    super();
    this.container = container;
    this.name = name;
  }

  public String getMd5() {
    return md5;
  }

  public File set(final File repository, final File data, final ContentType contentType)
      throws IOException, NoSuchAlgorithmException {
      log.debug(()->"ExternalFile.set(%s, %s, %s)".formatted(repository.getAbsolutePath(),
          data.getAbsolutePath(),
          contentType.value));
    delete(repository);
    final Md5InputStream src = new Md5InputStream(new FileInputStream(data));
    final File file = new File(repository, getPath("", contentType));
    if (!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }
    final OutputStream dest = new FileOutputStream(file);
    FileUtil.copy(src, dest, true);
    src.close();
    dest.close();
    log.debug(()->"ExternalFile.set copied %s -> %s".formatted(data.getAbsolutePath(), file.getAbsolutePath()));
    md5 = src.getMd5();
    return file;
  }

  public boolean delete(final File repository)
      throws IOException {
    boolean found = false;
    if (hasData()) {
      for (final File file : getFiles(repository.getParentFile())) {
        if (file.exists()) {
          file.delete();
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
