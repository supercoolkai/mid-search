package main_executables;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import structures.DataInterpreter;
import structures.DirectoryConfig;
import structures.Kattio;
import structures.Lemmatizer;
import structures.UrlEntry;

public class SimpleImplementation {
  static final Path BINARY_DIRECTORY = DirectoryConfig.BINARY_DIRECTORY.normalize();

  static final int URL_RETURN_AMT = 10;

  public static void main(String[] args) throws IOException {
    Kattio user = new Kattio();

    Lemmatizer lemmatizer = new Lemmatizer();

    String[] queries = user.nextLine().trim().split(" ");

    for (int i = 0; i < queries.length; i++) {
      queries[i] = queries[i].replaceAll("[ ,!?.\";:/\\-\'&<>\\\\#`!@$%^&*()+\\[\\]{}|~]", "");
    }

    long start = System.currentTimeMillis();
    List<UrlEntry> arr = new ArrayList<>();
    Map<UrlEntry, Double> urlMap = new HashMap<>();

    DataInterpreter urlRank = new DataInterpreter(URL_RETURN_AMT, BINARY_DIRECTORY);

    for (String query : queries) {
      query = lemmatizer.lemmatize(query.toLowerCase());

      long offset = urlRank.searchForTerm(query);
      UrlEntry[] newArr;
      try {
        newArr = urlRank.lexiconToUrls(offset);
      } catch (IOException e) {
        System.out.println("\"" + query + "\" not found!");
        continue;
      }
      for (UrlEntry entry : newArr) {
        UrlEntry currEntry = new UrlEntry(entry.getUrl(), entry.getId(),
            urlMap.getOrDefault(entry, 0.0) + entry.getScore());
        urlMap.put(currEntry, currEntry.getScore());
      }
    }

    for (UrlEntry e : urlMap.keySet()) {
      arr.add(e);
    }

    Collections.sort(arr, Collections.reverseOrder());

    long end = System.currentTimeMillis();

    for (int i = 0; i < Math.min(URL_RETURN_AMT, arr.size()); i++) {
      System.out.println(arr.get(i));
    }

    System.out.println("Found " + Math.min(URL_RETURN_AMT, arr.size()) + " URLS in " + (end - start) + " milliseconds");

    user.close();
  }
}
