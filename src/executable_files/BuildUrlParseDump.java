// First step of the pipeline, converts Nutch data to palatable data for the rest of the pipeline.
package executable_files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import structures.DirectoryConfig;
import structures.Kattio;

public class BuildUrlParseDump {
  // IO directories
  static final Path OUT_DIRECTORY = DirectoryConfig.TEMP_DIRECTORY.normalize();
  static final Path IN_DIRECTORY = DirectoryConfig.NUTCH_DIRECTORY.normalize();

  // IO file names
  static final String OUT_FILE_NAME = "url-parse-dump";
  static final String RAW_URL_FILE_NAME = "raw-url-dump";
  static final String TEMP_FILE_NAME = "temp-urlid-dump";
  static final String OFFSET_FILE_NAME = "pagerank-offset";
  static final String OUTLINK_LIST_NAME = "outlink-list";
  static final String IN_PARENT_FILE_NAME = "out";
  static final String IN_CHILD_FILE_NAME = "dump";

  // Misc. IO
  static final int DIR_LENGTH = 369;

  // Structure lengths for the hash tables:
  static final int PADDED_STRING_LENGTH = 256;
  static final int STRUCTURE_LENGTH_BYTES = 268;
  static final int OFFSET_STRUCTURE_BYTES = 272;
  static final int OUTLINK_STRUCTURE_LENGTH = 272;

  // "Master" structures lengths (Structures used for covering an aspect of the
  // whole database)
  static final int HASH_FILE_LENGTH = 800000;
  static final int MASTER_ARRAY_SIZE = 1000000; // Try to aim for around 50% more than the actual amount of url's. Just
                                                // to be safe.

  // Basic unit lengths:
  static final int URLID_LENGTH_BYTES = 4;
  static final int OUTLINK_COUNT_LENGTH = 4;
  static final int OFFSET_LONG_LENGTH = 8;
  static final int POINTER_LONG_LENGTH = 8;

  // Misc. structures
  static final int ESTIMATED_COLLISION_AMOUNT = 10;

  // Global IO
  static Kattio io;
  static PrintWriter outWriter;
  static PrintWriter urlWriter;

  // Mutable global variables
  static long overflowPointer = HASH_FILE_LENGTH * STRUCTURE_LENGTH_BYTES;
  static int uniqueUrlCount = 0;

  public static void main(String[] args) throws IOException {
    // -------------------------- File/IO Setup -------------------------------
    // Setup directories
    Files.createDirectories(OUT_DIRECTORY);

    // Setup Files
    File hashFileObj = OUT_DIRECTORY.resolve(TEMP_FILE_NAME).toFile();
    File offsetFileObj = OUT_DIRECTORY.resolve(OFFSET_FILE_NAME).toFile();
    File outlinkListFileObj = OUT_DIRECTORY.resolve(OUTLINK_LIST_NAME).toFile();

    RandomAccessFile hashFile = createFile(hashFileObj);
    RandomAccessFile offsetFile = createFile(offsetFileObj);
    RandomAccessFile outlinkListFile = createFile(outlinkListFileObj);

    hashFile.setLength((long) HASH_FILE_LENGTH * STRUCTURE_LENGTH_BYTES * ESTIMATED_COLLISION_AMOUNT);
    offsetFile.setLength((long) HASH_FILE_LENGTH * OFFSET_STRUCTURE_BYTES * ESTIMATED_COLLISION_AMOUNT);

    setupFile(OUT_DIRECTORY, OUT_FILE_NAME);
    setupFile(OUT_DIRECTORY, RAW_URL_FILE_NAME);

    // Setup IO
    io = new Kattio(IN_DIRECTORY.resolve(IN_PARENT_FILE_NAME + "1/" + IN_CHILD_FILE_NAME));
    outWriter = new PrintWriter(OUT_DIRECTORY.resolve(OUT_FILE_NAME).toFile());
    urlWriter = new PrintWriter(OUT_DIRECTORY.resolve(RAW_URL_FILE_NAME).toFile());

    // ---------------------------- 1st Pass ---------------------------------
    buildParseDump(hashFile);

    // ---------------------------- 2nd Pass ---------------------------------
    buildPageRank(hashFile, offsetFile, outlinkListFile);

    // ----------------------------- IO Closure -----------------------------
    offsetFile.close();
    outlinkListFile.close();
    hashFile.close();

    outWriter.close();
    urlWriter.close();
    io.close();
  }

  // Builds the basic url-parse-dump file, containing the URLs, URLIDs, and
  // ParseText. Also sets up the URL -> URLID conversion file.
  public static void buildParseDump(RandomAccessFile hashFile) throws IOException {
    uniqueUrlCount = 0;

    for (int i = 1; i <= DIR_LENGTH; i++) {
      // Local variable setup
      System.out.println("Pass 1 file " + i);

      io = new Kattio(IN_DIRECTORY.resolve(IN_PARENT_FILE_NAME + String.valueOf(i) + "/" + IN_CHILD_FILE_NAME));
      io.nextLine();

      String currUrl = "";
      String currParseText = "";
      String currRecno = "";
      String currLine;

      StringBuilder textBuilder = new StringBuilder();

      boolean gettingText = false;

      // Main logic loop
      while ((currLine = io.nextLine()) != null) {
        if (currLine.startsWith("Recno::")) {
          if (gettingText) {
            currParseText = textBuilder.toString();
          }

          // Valid URL reset case
          if (!currUrl.isEmpty() && !currParseText.isEmpty() && !currRecno.isEmpty()) {
            uniqueUrlCount++;

            outWriter.println("URL:: " + currUrl + "\nURLID:: " + uniqueUrlCount + "\nPARSETEXT:: \n" + currParseText);
            urlWriter.println(currUrl);

            // -------------------- Save hash codes in temp file ----------------------

            // Setup for the main loop
            long currHashCode = (currUrl.hashCode() & 0x7FFFFFFF) % HASH_FILE_LENGTH;
            byte[] fixed = padUrl(currUrl);

            hashFile.seek(currHashCode * STRUCTURE_LENGTH_BYTES + PADDED_STRING_LENGTH + URLID_LENGTH_BYTES);

            long pointer = hashFile.getFilePointer();

            // Main chain-following loop
            try {
              long currOffset = 0L;

              // Loop until reach the leaf node
              while ((currOffset = hashFile.readLong()) != 0L) {
                hashFile.seek(currOffset + PADDED_STRING_LENGTH + URLID_LENGTH_BYTES);
              }
            } catch (EOFException e) {
              System.out.println("EOFEXCEPTION, POINTER: " + pointer + " SIZE: " + hashFile.length() + " RECNO: "
                  + currRecno + " MSG: " + e.getMessage());
            }

            // Success case
            hashFile.seek(hashFile.getFilePointer() - STRUCTURE_LENGTH_BYTES);
            hashFile.write(fixed);
            hashFile.writeInt(uniqueUrlCount);
            hashFile.writeLong(overflowPointer);

            overflowPointer += STRUCTURE_LENGTH_BYTES;
          }

          // Variable reset
          gettingText = false;
          currRecno = currLine;
          currUrl = "";
          currParseText = "";
        }

        if (currLine.startsWith("URL::")) {
          currUrl = currLine.substring(5).trim();
        }

        // Start of parse text
        if (currLine.startsWith("ParseText::")) {
          gettingText = true;
          textBuilder = new StringBuilder();
        }

        // Middle/end of parse text
        else if (gettingText) {
          if (currLine.endsWith("::") || currLine.contains(":: ")) {
            gettingText = false;
          } else {
            textBuilder.append(currLine).append('\n');
          }
        }
      }
    }

    System.out.println("1ST PASS DONE \nUNIQUE URL COUNT: " + uniqueUrlCount + "\n2ND PASS STARTING");
  }

  // Sets up the foundation for the PageRank system, setting up the offset hash
  // table -> outlink list pipeline.
  public static void buildPageRank(RandomAccessFile hashFile, RandomAccessFile offsetFile,
      RandomAccessFile outlinkListFile) throws IOException {
    // Master variable setup/reset
    int[] hyperlinkArr = new int[MASTER_ARRAY_SIZE]; // Could be set to uniqueUrlCount+1, but MASTER_ARRAY_SIZE is
                                                     // safer.

    uniqueUrlCount = 0;
    overflowPointer = HASH_FILE_LENGTH * OFFSET_STRUCTURE_BYTES;

    for (int i = 1; i <= DIR_LENGTH; i++) {
      // Local variable setup
      System.out.println("Pass 2 file " + i);
      io = new Kattio(IN_DIRECTORY.resolve(IN_PARENT_FILE_NAME + String.valueOf(i) + "/" + IN_CHILD_FILE_NAME));
      io.nextLine();

      String currUrl = "";
      String currRecno = "";
      String currLine;
      String currParseText = "";

      StringBuilder textBuilder = new StringBuilder();

      List<String> outLinks = new ArrayList<>();

      boolean gettingText = false;

      // Main logic loop
      while ((currLine = io.nextLine()) != null) {

        // Reset-case scenario
        if (currLine.startsWith("Recno::")) {
          if (gettingText) {
            currParseText = textBuilder.toString();
          }

          // Valid URL reset case
          if (!currUrl.isEmpty() && !currParseText.isEmpty() && !currRecno.isEmpty()) {
            uniqueUrlCount++;

            // -------------- Save outlinks in file -----------------
            for (String url : outLinks) {
              int urlId = urlIdFromUrl(hashFile, url);

              if (urlId < 0) {
                continue;
              }

              byte[] fixed = padUrl(url);
              outlinkListFile.write(fixed);
            }

            // Local variable setup
            long currHashCode = (currUrl.hashCode() & 0x7FFFFFFF) % HASH_FILE_LENGTH;
            long currPointer = outlinkListFile.getFilePointer();
            int id = urlIdFromUrl(hashFile, currUrl);
            byte[] fixed = padUrl(currUrl);

            // Outlink list file setup
            outlinkListFile.write(fixed);
            outlinkListFile.writeInt(hyperlinkArr[id]);

            // Offset file setup

            // Setup for the main loop
            offsetFile.seek(currHashCode * OFFSET_STRUCTURE_BYTES + PADDED_STRING_LENGTH + POINTER_LONG_LENGTH);

            long pointer = offsetFile.getFilePointer();

            // Main chain-following loop
            try {
              long currOffset = 0L;

              while ((currOffset = offsetFile.readLong()) != 0L) {
                offsetFile.seek(currOffset + PADDED_STRING_LENGTH + POINTER_LONG_LENGTH);
              }
            } catch (EOFException e) {
              System.out.println("EOFEXCEPTION, POINTER: " + pointer + " SIZE: " + hashFile.length() + " RECNO: "
                  + currRecno + " MSG: " + e.getMessage());
            }

            // Success case
            offsetFile.seek(offsetFile.getFilePointer() - OFFSET_STRUCTURE_BYTES);
            offsetFile.write(fixed);
            offsetFile.writeLong(currPointer);
            offsetFile.writeLong(overflowPointer);

            overflowPointer += OFFSET_STRUCTURE_BYTES;
          }

          // Variable reset
          outLinks.clear();
          gettingText = false;
          currRecno = currLine;
          currUrl = "";
          currParseText = "";
        }

        // Save outlinks
        if (currLine.startsWith("outlink: toUrl: ")) {
          hyperlinkArr[uniqueUrlCount]++;
          outLinks.add(currLine.substring(15).trim());
        }

        // Misc. scenarios
        if (currLine.startsWith("URL::")) {
          currUrl = currLine.substring(5).trim();
        }

        if (currLine.startsWith("ParseText::")) {
          gettingText = true;
          textBuilder = new StringBuilder();
        }

        else if (gettingText) {
          if (currLine.endsWith("::") || currLine.contains(":: ")) {
            gettingText = false;
          } else {
            textBuilder.append(currLine).append('\n');
          }
        }
      }
    }
  }

  // Creates/overwrites a RandomAccessFile of choice
  public static RandomAccessFile createFile(File fileObj) {
    // Variable setup
    RandomAccessFile file;

    // Try to create the RandomAccessFile. If already existing, then warns user
    // about overwriting.
    try {
      if (fileObj.exists()) {
        System.err.println("File already exists, overwriting!");
      }

      file = new RandomAccessFile(fileObj, "rw");
      file.setLength(0);

      System.out.println("Created file successfully!");

      return file;
    }

    // Fail case scenario, returns null and catches the exception while warning the
    // user.
    catch (IOException ex) {
      System.err.println("IOException: ");
      ex.printStackTrace();
    }

    return null;
  }

  // Converts a given URL into its urlID if it exists in the hash table.
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

// "What's a high tier vibe coder to a low tier thinker"
