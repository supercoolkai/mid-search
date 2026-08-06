// Modified Kattio to fit project as necessary 
package structures;

import java.util.*;
import java.io.*;
import java.nio.file.Path;

public class Kattio extends PrintWriter {
	// Uninitialized variables:
	BufferedReader reader;
	StringTokenizer lineTokenizer;
 
	// -------------------- System Constructor ----------------------
	public Kattio() throws IOException{
		this(System.in, System.out);
	}
 
	// --------------------- IOStream IO --------------------------
	public Kattio(InputStream i, OutputStream o)throws IOException {
		super(o);
		reader = new BufferedReader(new InputStreamReader(i));
	}
	
	// ------------------------- (Binary) String-Based IO --------------------------
	public Kattio(String str, String str2)throws IOException{
	    super(str2);
	    reader = new BufferedReader(new FileReader(str));
	}
	
	// ------------------------ String I, OutStream O --------------------
	@SuppressWarnings("resource")
	public Kattio(String str, OutputStream out)throws IOException{
	    super(out);
	    reader = new BufferedReader(new FileReader(str));
	}
	
	// --------------------------- (Unary) String-Based IO -----------------------
	public Kattio(String str)throws IOException{
	    super(System.out);
	    reader = new BufferedReader(new FileReader(str));
	}
	
	// -------------------------- (Unary) Path-Based IO -------------------------
	public Kattio(Path file) throws IOException{
		super(System.out);
		reader = new BufferedReader(new FileReader(file.toFile()));
	}
	
	// --------------------------- (Binary) Path-Based IO ------------------------
	public Kattio (Path file, Path out) throws IOException{
		super(out.toFile());
		reader = new BufferedReader(new FileReader(file.toFile()));
	}
	
	// ---------------------------- Read Token ------------------------------
	public String next()throws IOException{
		// Validate that there are tokens left before refilling the lineTokenizer
	    while(lineTokenizer == null || !lineTokenizer.hasMoreTokens()){
	        lineTokenizer = new StringTokenizer(reader.readLine());
	    }
	   
	    return lineTokenizer.nextToken();
	}
	
	// -------------------------- Read Line ----------------------
	public String nextLine() throws IOException{
	    return reader.readLine();
	}
	
	// --------------------------- Read Int ---------------------
	public int nextInt()throws IOException{
	    return Integer.parseInt(next());
	}
	
	// --------------------------- Read Long --------------------------
	public long nextLong() throws IOException{
	    return Long.parseLong(next());
	}
	
	// ---------------------------- Read Double ------------------------
	public double nextDouble() throws IOException{
	    return Double.parseDouble(next());
	}
	
	// ---------------------------- Read Float -----------------------
	public float nextFloat() throws IOException{
	    return Float.parseFloat(next());
	}
}