// Converts cleaned-up url-parse_text dump file to url-> id, score dump file. Yet to be encoded in binary
package executable_files;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import structures.DirectoryConfig;
import structures.Kattio;
import structures.Lemmatizer;
import structures.UrlEntry;

public class BM25FromDump {
  // IO directories
  static final Path OUT_DIRECTORY = DirectoryConfig.DUMP_DIRECTORY.normalize();
  static final Path IN_DIRECTORY = DirectoryConfig.TEMP_DIRECTORY.normalize();

  // IO file names
  static final String OUT_FILE_NAME = "url-score-dump";
  static final String IN_FILE_NAME = "url-parse-dump";
  static final String RAW_URL_FILE_NAME = "raw-url-dump";
  static final String TEMP_FILE_NAME = "temp-urlid-dump";
  static final String OFFSET_FILE_NAME = "pagerank-offset";
  static final String OUTLINK_LIST_NAME = "outlink-list";

  // "Master" structures lengths (Structures used for covering an aspect of the
  // whole database):
  static final int HASH_FILE_LENGTH = 800000;
  static final int MASTER_ARRAY_SIZE = 1000000;

  // Structure lengths:
  static final int PADDED_STRING_LENGTH = 256;
  static final int OUTLINK_STRUCTURE_LENGTH = 272;
  static final int STRUCTURE_LENGTH_BYTES = 268;
  static final int OFFSET_STRUCTURE_BYTES = 272;

  // Basic unit lengths:
  static final int POINTER_LONG_LENGTH = 8;

  // PageRank global variables
  static final double PAGERANK_WEIGHT = 0.75;
  static final double DAMPER_VALUE = 0.85;

  static final int PAGERANK_REPEAT_AMOUNT = 45; // the more you repeat, the more accurate pagerank scores are.

  // Global IO
  static Kattio in;
  static Kattio urlReader;
  static PrintWriter out;

  // Mutable global variables
  static double[] pagerankScoresPerUrl;

  static Map<String, Integer> documentFrequency;
  static Map<String, Integer> countPerWord;
  static Map<String, ArrayList<UrlEntry>> urlMap;
  static Map<String, Integer> entryToLen;

  static double avgDocLen;
  static double totalDocs;

  public static void main(String[] args) throws IOException {
    // ---------------------------------- File/IO Setup
    // ------------------------------------
    // Setup directories
    Files.createDirectories(OUT_DIRECTORY);

    // Setup files
    File outlinkListObj = IN_DIRECTORY.resolve(OUTLINK_LIST_NAME).toFile();
    File offsetFileObj = IN_DIRECTORY.resolve(OFFSET_FILE_NAME).toFile();
    File tempFileObj = IN_DIRECTORY.resolve(TEMP_FILE_NAME).toFile();

    RandomAccessFile hashFile = new RandomAccessFile(tempFileObj, "r");
    RandomAccessFile outlinkList = new RandomAccessFile(outlinkListObj, "r");
    RandomAccessFile offset = new RandomAccessFile(offsetFileObj, "r");

    setupFile(OUT_DIRECTORY, OUT_FILE_NAME);

    // Setup IO
    in = new Kattio(IN_DIRECTORY.resolve(IN_FILE_NAME));
    urlReader = new Kattio();
    out = new PrintWriter(OUT_DIRECTORY.resolve(OUT_FILE_NAME).toFile());

    // Setup PageRank master array
    pagerankScoresPerUrl = new double[MASTER_ARRAY_SIZE];

    Arrays.fill(pagerankScoresPerUrl, 1 - DAMPER_VALUE);

    // ---------------------------- PageRank Master Array Setup
    // -----------------------------
    setupPageRank(hashFile, offset, outlinkList);

    // ------------------------------- Setup BM25 Necessities
    // --------------------------------
    setupScores();

    // -------------------------------- Output BM25 Scores
    // ------------------------------------------
    outputScores();

    // ------------------------------------- IO Closure
    // -------------------------------------------
    offset.close();
    outlinkList.close();
    urlReader.close();
    out.close();
    in.close();
  }

  // Sets up the PageRank master array as a first pass
  public static void setupPageRank(RandomAccessFile hashFile, RandomAccessFile offset, RandomAccessFile outlinkList)
      throws IOException {
    // Loop N amount of times (N being the PAGERANK_REPEAT_AMOUNT). As N goes up,
    // accuracy goes up as well.
    for (int i = 0; i < PAGERANK_REPEAT_AMOUNT; i++) {
      // Debug statement to check if loop is running
      System.out.println("Pass " + (i + 1));

      // Setup IO
      urlReader = new Kattio(IN_DIRECTORY.resolve(IN_FILE_NAME));

      // Setup local variables
      String currUrl = "";
      int id = -1;

      // Main logic loop
      while ((currUrl = urlReader.nextLine()) != null) {
        // ---------------- OBJECTIVE: Find the offset in the offset file to get the
        // given URL's outlinks from the Outlink List file.

        // Setup necessary variables for the upcoming loop
        id = urlIdFromUrl(hashFile, currUrl);
        byte[] fixed = padUrl(currUrl);
        long hashCode = (currUrl.hashCode() & 0x7FFFFFFF) % HASH_FILE_LENGTH;

        offset.seek(hashCode * OFFSET_STRUCTURE_BYTES + PADDED_STRING_LENGTH + POINTER_LONG_LENGTH);

        long pointer = offset.getFilePointer();
        long currOffset = 0L;

        // Main chain-following loop
        try {
          currOffset = offset.readLong();

          offset.seek(currOffset);

          byte[] currUrlBytes = new byte[PADDED_STRING_LENGTH];
          offset.readFully(currUrlBytes);

          offset.seek(currOffset + PADDED_STRING_LENGTH + POINTER_LONG_LENGTH);

          // Loop until either reaches the leaf node or the target node
          while ((currOffset = offset.readLong()) != 0L && !Arrays.equals(currUrlBytes, fixed)) {
            offset.seek(currOffset);

            offset.readFully(currUrlBytes);

            offset.seek(currOffset + PADDED_STRING_LENGTH + POINTER_LONG_LENGTH);
          }
        } catch (EOFException e) {
          System.out
              .println("EOFEXCEPTION, POINTER: " + pointer + " SIZE: " + offset.length() + " MSG: " + e.getMessage());
        }

        // Check if offset is valid
        if (currOffset != 0L) {
          // ----------------- OBJECTIVE: Follow the known offset and get the values from
          // the Outlink List file

          // Setup necessary variables
          offset.seek(offset.getFilePointer() - OFFSET_STRUCTURE_BYTES + PADDED_STRING_LENGTH);

          long currPointer = offset.readLong();

          outlinkList.seek(currPointer);

          byte[] finalCheck = new byte[PADDED_STRING_LENGTH];
          outlinkList.readFully(finalCheck);

          // Do the final check to ensure we have the right URL
          if (!Arrays.equals(finalCheck, fixed)) {
            continue;
          }

          // Setup variables for the PageRank score calculation
          double outlinkCount = (double) outlinkList.readInt();
          double contribution = pagerankScoresPerUrl[id] / outlinkCount;
          int currId = -1;
          byte[] currOutlink = new byte[PADDED_STRING_LENGTH];
          String stringOutlink = "";

          // Loop through all outlinks to add the contribution value to each one of their
          // respective scores
          for (int j = 0; j < outlinkCount; j++) {
            outlinkList.readFully(currOutlink);
            stringOutlink = new String(currOutlink, StandardCharsets.ISO_8859_1);

            // Check if the outlink exists in the database
            if ((currId = urlIdFromUrl(hashFile, stringOutlink)) != -1) {
              pagerankScoresPerUrl[currId] += DAMPER_VALUE * contribution;
            }
          }
        }
      }

    }
  }

  // Sets up all necessary information for BM25 scoring and outputs it to the
  // global HashMaps.
  public static void setupScores() throws IOException {
    // Local variable setup
    Lemmatizer lemmatizer = new Lemmatizer();
    String currLine = "";
    String url = "";
    String urlId = "";

    double currLen = 0;
    double totalLen = 0;

    totalDocs = 0;
    documentFrequency = new HashMap<>();
    countPerWord = new HashMap<>();
    urlMap = new HashMap<>();
    entryToLen = new HashMap<>();

    // Main logic loop
    while ((currLine = in.nextLine()) != null) {
      if (currLine.startsWith("URL::")) {
        totalDocs++;

        // Add all word information if the previous URL is valid
        if (!url.isEmpty() && !countPerWord.isEmpty()) {
          for (String word : countPerWord.keySet()) {
            // Update IDF HashMap
            documentFrequency.merge(word, 1, Integer::sum);

            // Setup the TF value and UrlEntry
            double tf = (double) countPerWord.get(word);
            UrlEntry entry = new UrlEntry(url, urlId, tf);

            // Check if the URL exists in the entryToLen HashMap to ensure you're not
            // overwriting a previous entry
            if (!entryToLen.containsKey(entry.getUrl())) {
              entryToLen.put(entry.getUrl(), (int) currLen);
            }

            // Add the entry to the urlMap
            urlMap.computeIfAbsent(word, k -> new ArrayList<>()).add(entry);
          }
        }

        // Reset local variables
        currLen = 0;
        countPerWord.clear();
        url = currLine;
        continue;
      }

      if (currLine.startsWith("URLID::")) {
        urlId = currLine;
        continue;
      }

      // --------------- ParseText Case -----------------

      // Setup StringTokenizer
      StringTokenizer lineTokenizer = new StringTokenizer(currLine, " ,!?.\";:/-\'<>\\#`!@$%^&*()+[]{}|~");

      // Update length variables
      currLen += lineTokenizer.countTokens();
      totalLen += lineTokenizer.countTokens();

      // Extract all text from the ParseText
      while (lineTokenizer.hasMoreTokens()) {
        String token = lemmatizer.lemmatize(lineTokenizer.nextToken().toLowerCase());
        countPerWord.merge(token, 1, Integer::sum);
      }
    }

    // Update average document length
    avgDocLen = totalLen / totalDocs;

    // Final pass to get last URL done as well
    if (!url.equals("") && !countPerWord.isEmpty()) {
      for (String word : countPerWord.keySet()) {
        documentFrequency.merge(word, 1, Integer::sum);

        double tf = (double) countPerWord.get(word);
        UrlEntry entry = new UrlEntry(url, urlId, tf);

        if (!entryToLen.containsKey(entry.getUrl()))
          entryToLen.put(entry.getUrl(), (int) currLen);

        urlMap.computeIfAbsent(word, k -> new ArrayList<>()).add(entry);
      }
    }
  }

  // Assigns all UrlEntries their BM25 scores, and outputs it into the
  // url-score-dump file.
  public static void outputScores() throws IOException {
    // Iterate through each word's UrlEntry ArrayList to update their scores
    for (String k : urlMap.keySet()) {
      // Iterate through each UrlEntry in the ArrayList
      for (UrlEntry entry : urlMap.get(k)) {
        // Get the ID to be able to check the PageRank score of the URL
        int id = Integer.parseInt(entry.getId().trim().substring(7).trim());

        // Get numerator/denominator of the formula so that there isn't too much code on
        // one line
        double numerator = entry.getScore() * Math.log((double) (totalDocs - documentFrequency.getOrDefault(k, 0) + 0.5)
            / (documentFrequency.getOrDefault(k, 0) + 0.5)) * (2.3);
        double denominator = entry.getScore()
            + 1.3 * (0.25 + 0.75 * (((double) entryToLen.get(entry.getUrl())) / avgDocLen));

        // Get PageRank score
        double inlinkScore = pagerankScoresPerUrl[id];

        // Add the PageRank score to the BM25 score.
        entry.setScore(numerator / denominator + (PAGERANK_WEIGHT * inlinkScore));
      }

      // Sort the scored entries to be able to binary search
      Collections.sort(urlMap.get(k), Collections.reverseOrder());
    }

    // Output to the url-score-dump File
    for (String k : urlMap.keySet()) {
      out.println("KEYWORD:: " + k);

      for (UrlEntry entry : urlMap.get(k)) {
        out.println(entry.toString());
      }
    }
  }

  // Converts a string into a padded 256-byte byte[]. If the string is over 256
  // bytes, cuts off any extra characters.
  public static byte[] padUrl(String currUrl) {
    // Variable setup
    byte[] fixed = new byte[PADDED_STRING_LENGTH];

    // Cut off any extra characters
    if (currUrl.length() > PADDED_STRING_LENGTH) {
      currUrl = currUrl.substring(0, PADDED_STRING_LENGTH);
    }

    // Convert the string into raw bytes
    byte[] raw = currUrl.getBytes(StandardCharsets.ISO_8859_1);
    int len = Math.min(raw.length, PADDED_STRING_LENGTH);
    System.arraycopy(raw, 0, fixed, 0, len);

    // Pad extra spaces
    for (int j = len; j < PADDED_STRING_LENGTH; j++) {
      fixed[j] = ' ';
    }

    return fixed;
  }

  public static int urlIdFromUrl(RandomAccessFile hashFile, String url) throws IOException {
    // Variable setup
    long currHashCode = (url.hashCode() & 0x7FFFFFFF) % HASH_FILE_LENGTH;
    String currUrl = url;
    byte[] fixed = padUrl(currUrl);
    byte[] currArr = new byte[PADDED_STRING_LENGTH];

    // Kickstart the while loop by doing the first step outside.
    hashFile.seek(currHashCode * STRUCTURE_LENGTH_BYTES);
    hashFile.readFully(currArr);

    int currId = hashFile.readInt();
    long currOffset = currHashCode * STRUCTURE_LENGTH_BYTES;
    boolean loopHasRan = false;

    // Main chain-following loop
    while (!Arrays.equals(currArr, fixed) && currOffset != 0L || !loopHasRan) {
      // Seek to given offset
      hashFile.seek(currOffset);

      // Read in variables
      hashFile.readFully(currArr);
      currId = hashFile.readInt();
      currOffset = hashFile.readLong();

      // Update the failsafe variable
      loopHasRan = true;
    }

    // File not found case
    if (currOffset == 0L && !Arrays.equals(currArr, fixed)) {
      return -1;
    }

    return currId;
  }

  // Sets up a file with a given directory and file name
  public static void setupFile(Path dir, String name) {
    // Try to create a new text file
    try {
      // Create the path
      Path filePath = dir.resolve(name);

      // Create a file at that path
      Files.createFile(filePath);

      System.out.println("File created successfully at: " + filePath.toAbsolutePath());
    }

    // Warn the user about overwriting if file already exists
    catch (java.nio.file.FileAlreadyExistsException e) {
      System.err.println("File already exists, overwriting file!");
    }

    // Catch any misc. exceptions and warn the user
    catch (IOException e) {
      System.err.println("IO error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
