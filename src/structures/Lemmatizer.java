// What's a high tier vibe coder to a low tier thinker

// Lemmatizes inputted words for easy access to lemmatization
package structures;

import java.util.*;
import java.io.*;
import java.nio.file.Path;

public class Lemmatizer {
  // Directories:
  static final Path LEMMA_DIR = DirectoryConfig.LEMMA_DIRECTORY.normalize();

  // File names:
  static final String FILE_NAME = "lookup-table.txt";

  // Global IO:
  Kattio reader;

  // Global map:
  Map<String, String> formToRoot;

  // ----------------------- Constructor ----------------------------
  public Lemmatizer() throws IOException {

    // Initialize/create necessary variables
    reader = new Kattio(LEMMA_DIR.resolve(FILE_NAME));
    formToRoot = new HashMap<>();
    String nextLine = "";

    // Pre-read all lemmatization pair into formToRoot map
    while ((nextLine = reader.nextLine()) != null) {
      StringTokenizer st = new StringTokenizer(nextLine);

      String root = st.nextToken();
      String form = st.nextToken();

      formToRoot.put(form, root);
    }
  }

  // ------------------------------- Getter ------------------------------
  public String lemmatize(String str) {
    // Check if in map, otherwise return the input (root word)
    if (formToRoot.containsKey(str)) {
      return formToRoot.get(str);
    }

    return str;
  }

}
