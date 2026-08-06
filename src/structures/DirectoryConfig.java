package structures;

import java.net.URISyntaxException;
import java.nio.file.Path;

public final class DirectoryConfig {

  public static final Path PROJECT_DIRECTORY;
  public static final Path DUMP_DIRECTORY;
  public static final Path BINARY_DIRECTORY;
  public static final Path TEMP_DIRECTORY;
  public static final Path LEMMA_DIRECTORY;
  public static final Path NUTCH_DIRECTORY;

  static {
    try {
      PROJECT_DIRECTORY = Path.of(
          DirectoryConfig.class
              .getProtectionDomain()
              .getCodeSource()
              .getLocation()
              .toURI())
          .getParent();

      LEMMA_DIRECTORY = PROJECT_DIRECTORY.resolve("lemmatization-list");
      NUTCH_DIRECTORY = PROJECT_DIRECTORY.resolve("nutch-out");
      BINARY_DIRECTORY = PROJECT_DIRECTORY.resolve("binary-files");
      TEMP_DIRECTORY = PROJECT_DIRECTORY.resolve("temp-data-dump");
      DUMP_DIRECTORY = PROJECT_DIRECTORY.resolve("dump-files");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
