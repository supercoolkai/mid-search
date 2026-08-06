// Interprets data of binary files for easy access to urlentries from them
// Customizable return amount to change it easily

package structures;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class DataInterpreter {
  // Global constants
  static final int PADDED_STRING_LENGTH = 128;
  static final int TERM_ENTRY_SIZE = 144;
  static final int URL_ENTRY_SIZE = 20;

  // Uninitialized variables
  int urlReturnCount;

  // Global files
  RandomAccessFile docs;
  RandomAccessFile header;
  RandomAccessFile lexicon;

  // --------------------------- Constructor ------------------------------
  public DataInterpreter(int urlReturnCnt, Path directory) throws FileNotFoundException {
    // Initialize return count variable
    this.urlReturnCount = urlReturnCnt;

    // Setup global binary files
    File docObject = directory.resolve("docs").toFile();
    File headerObject = directory.resolve("header").toFile();
    File lexiconObject = directory.resolve("lexicon").toFile();

    docs = new RandomAccessFile(docObject, "r");
    header = new RandomAccessFile(headerObject, "r");
    lexicon = new RandomAccessFile(lexiconObject, "r");
  }

  // ----------------------------------- Step One
  // -------------------------------------------

  // Returns the inputted term's position in the lexicon.
  public long searchForTerm(String term) throws IOException {
    // Setup local variables
    long lowestTerm = 0;
    long highestTerm = lexicon.length() / TERM_ENTRY_SIZE - 1;

    byte[] byteArr = new byte[PADDED_STRING_LENGTH];

    // Main logic loop
    while (lowestTerm <= highestTerm) {
      // Setup this iteration's necessary variables
      long middleTerm = (lowestTerm + (highestTerm - lowestTerm) / 2);
      long offset = middleTerm * TERM_ENTRY_SIZE;

      // Check the current offset and see whether it matches the inputted term
      lexicon.seek(offset);

      lexicon.readFully(byteArr);

      // Check if the spacing is invalid (landed in the middle of an entry!)
      if ((lexicon.getFilePointer() - PADDED_STRING_LENGTH) % TERM_ENTRY_SIZE != 0) {
        System.out.println("NOT STARTING ON A VALID SPACE");
        throw new IOException();
      }

      // Setup necssary variables for comparison
      String str = new String(byteArr, "ISO-8859-1").trim();
      int compareResult = str.compareTo(term);

      // If the offset's string is lexicographically lower, then cut out that half
      if (compareResult < 0) {
        lowestTerm = middleTerm + 1;
        continue;
      }

      // If the offset's string is lexicographically higher, then cut out that half.
      else if (compareResult > 0) {
        highestTerm = middleTerm - 1;
        continue;
      }

      return offset;
    }

    // Case if term is not found in lexicon
    return -1;
  }

  // ----------------------------------------------------- Step Two
  // -----------------------------------------------

  // With an inputted offset from the lexicon file, return the top urlReturnCnt
  // URLs in the index file.
  public UrlEntry[] lexiconToUrls(long offset) throws IOException {
    // Check if entry is valid
    if (offset == -1) {
      throw new IOException();
    }

    // Setup local variables
    long len = -1;
    long from = -1;

    byte[] byteArr = new byte[TERM_ENTRY_SIZE - PADDED_STRING_LENGTH];

    lexicon.seek(offset + PADDED_STRING_LENGTH);

    lexicon.readFully(byteArr);

    ByteBuffer buf = ByteBuffer.wrap(byteArr);

    // Read in the from and len variables
    from = buf.getLong();
    len = Math.min(buf.getLong(), urlReturnCount);

    // Check if either of the from or len are invalid
    if (len == -1 || from == -1) {
      System.out.println("Not in file");
      throw new IOException();
    }

    // Setup UrlEntry array and prepare for the loop
    UrlEntry[] arr = new UrlEntry[(int) len];

    header.seek(from);

    // Main logic loop
    for (int i = 0; i < arr.length; i++) {
      // Setup local variables
      UrlEntry curr;
      byte[] currBytes = new byte[URL_ENTRY_SIZE];

      // Read in the current entry
      header.readFully(currBytes);

      // Extract each part of the entry with a ByteBuffer
      ByteBuffer currBuf = ByteBuffer.wrap(currBytes);

      // Extract offset to docs (to access the URL later on)
      long offsetToDocs = currBuf.getLong();

      // Extract URLID and currScore and save them immediately
      String currId = "URLID:: " + String.valueOf(currBuf.getInt());

      double currScore = currBuf.getDouble();

      // Seek to the URL with the offset provided and save it\
      docs.seek(offsetToDocs);

      String url = "URL:: " + docs.readUTF();

      // Finalize the entry and add it to the array
      curr = new UrlEntry(url, currId, currScore);

      arr[i] = curr;
    }

    return arr;
  }

  // --------------------------------------------- File Closure
  // ---------------------------------------

  // Closes all binary files.
  public void close() throws IOException {
    docs.close();
    header.close();
    lexicon.close();
  }
}
