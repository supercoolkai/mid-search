package main_executables;

import java.io.File;

public class BuildDatabase {
  static final String stepPackage = "executable_files";
  static final String classPath;
  static {
    String path;
    try {
      path = new File(BuildDatabase.class
          .getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toURI())
          .getAbsolutePath();
    } catch (Exception e) {
      path = null; // Genuinely can't think of a case where this would trigger but java is a bitch
                   // and wont let me get rid of this
    }
    classPath = path;
  }

  static final String step1 = "BuildUrlParseDump";
  static final String step2 = "BM25FromDump";
  static final String step3 = "DumpToBinaryData";

  public static void main(String[] args) throws Exception {
    System.out.println(classPath); // lil debug statement

    runStep(step1);
    runStep(step2);
    runStep(step3);
  }

  static void runStep(String className) throws Exception {
    Process p = new ProcessBuilder("java",
        "-cp",
        classPath,
        stepPackage + "." + className).inheritIO().start();

    int code = p.waitFor();
    if (code != 0)
      throw new RuntimeException(className + " failed to start with exit code " + code);
  }
}
