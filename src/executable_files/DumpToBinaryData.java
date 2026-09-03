
// Converts BM25 score dump file to binary files

package executable_files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.nio.file.Files;

import structures.DirectoryConfig;
import structures.Kattio;
import structures.LexiconEntry;

public class DumpToBinaryData {
  // IO directories
  static final int PADDED_STRING_LENGTH = 128;

  static final Path OUTPUT_DIRECTORY = DirectoryConfig.BINARY_DIRECTORY.normalize();
  static final Path INPUT_DIRECTORY = DirectoryConfig.DUMP_DIRECTORY.normalize();

  // IO file names
  static final String INPUT_FILE_NAME = "url-score-dump";
  static final String DOC_FILE_NAME = "docs";
  static final String LEXICON_FILE_NAME = "lexicon";
  static final String INDEX_FILE_NAME = "header";

  // Global IO
  static Kattio io;

  // Global constants
  static final int URLLISTLENGTH = 10000000;

  // Global mutable variables
  static List<LexiconEntry> lexiconEntryList;

  public static void main(String[] args) throws IOException {
    // ----------------------------------- File/IO Setup
    // ----------------------------------------
    // Setup directories
    Files.createDirectories(OUTPUT_DIRECTORY);

    // Setup files
    File fileObj = OUTPUT_DIRECTORY.resolve(DOC_FILE_NAME).toFile();
    File iFileObj = OUTPUT_DIRECTORY.resolve(INDEX_FILE_NAME).toFile();
    File lFileObj = OUTPUT_DIRECTORY.resolve(LEXICON_FILE_NAME).toFile();

    RandomAccessFile file = createFile(fileObj);
    RandomAccessFile iFile = createFile(iFileObj);
    RandomAccessFile lFile = createFile(lFileObj);

    // Setup IO
    io = new Kattio(INPUT_DIRECTORY.resolve(INPUT_FILE_NAME));

    // -------------------------------- Build Index and Document Files
    // --------------------------------
    buildIndexDocs(file, iFile);

    // ---------------------------- Output to Lexicon File
    // --------------------------------
    buildLexiconFile(lFile);

    // ------------------------------- Close IO -----------------------------------
    io.close();
    file.close();
    iFile.close();
    lFile.close();
  }

  // Outputs each LexiconEntry into the Lexicon binary file (redirects each word
  // to its entry in the index file)
  public static void buildLexiconFile(RandomAccessFile lFile) throws IOException {
    // Setup the list for output into lexicon file
    Collections.sort(lexiconEntryList);

    // Output each entry in the list, sorted alphabetically
    for (LexiconEntry entry : lexiconEntryList) {
      // Begin output
      lFile.writeBytes(entry.getWord());

      // Ensure the word is padded correctly, so that the whole document doesn't get
      // messed up.
      if (entry.getWord().getBytes(StandardCharsets.ISO_8859_1).length != PADDED_STRING_LENGTH) {
        System.out.println("Bad word");
      }

      // Continue output as usual
      lFile.writeLong(entry.getOffset());
      lFile.writeLong(entry.length());
    }
  }

  // Goes through the BM25 dump and converts it to binary into the raw urls, and
  // the information for those documents.
  public static void buildIndexDocs(RandomAccessFile file, RandomAccessFile iFile) throws IOException {
    // Setup local variables
    String nextLine = "";

    String currWord = "";
    long currPointer = 0;
    int currId = 0;
    String currUrl = "";
    Long currIPointer = (long) -1;
    Long currCount = (long) 0;

    boolean offsetNeeded = false;

    long[] docOffsets = new long[URLLISTLENGTH];
    Arrays.fill(docOffsets, -1);

    lexiconEntryList = new ArrayList<>();

    // Main logic loop
    while ((nextLine = io.nextLine()) != null) {
      if (nextLine.startsWith("KEYWORD::")) {
        // Setup local variables
        String word = nextLine.substring(10);

        // Clean up word
        byte[] fixed = padUrl(word);

        // If entry is valid, then add to lexicon
        if (!currWord.isEmpty() && currIPointer != -1 && currCount != 0) {
          lexiconEntryList.add(new LexiconEntry(currWord, currIPointer, currCount));
        }

        // Reset local variables
        currWord = new String(fixed, StandardCharsets.ISO_8859_1);
        currCount = (long) 0;
        offsetNeeded = true;

        continue;
      }

      else if (nextLine.startsWith("URLID::")) {
        // Setup local variables
        currId = Integer.parseInt(nextLine.substring(8).trim());
        currPointer = file.getFilePointer();

        // If first instance of URL, add the current offset to docOffsets
        if (docOffsets[currId] == -1) {
          docOffsets[currId] = currPointer;
          file.writeUTF(currUrl);

        }

        // Set currPointer to the current offset
        else {
          currPointer = docOffsets[currId];
        }

        // Update currCount to account for the new URL structure
        currCount++;

        // If not acquired yet, update currIPointer
        if (offsetNeeded) {
          currIPointer = iFile.getFilePointer();
          offsetNeeded = false;
        }

        // Write info
        iFile.writeLong(currPointer);
        iFile.writeInt(currId);

        continue;
      }

      else if (nextLine.startsWith("URL::")) {
        // Update local variable
        currUrl = nextLine.substring(6).trim();

        continue;
      }

      // Write current score into indexFile
      iFile.writeDouble(Double.parseDouble(nextLine.substring(8).trim()));
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
}
