// Container class to easily store lexicon entries in the DumpToBinaryData
package structures;

public class LexiconEntry implements Comparable<LexiconEntry>{
	// Uninitialized variables
    private String word;
    private Long offset;
    private Long length;
    
    // ------------------ Constructor ----------------
    public LexiconEntry(String word, Long offset, Long length){
        this.word = word;
        this.offset = offset;
        this.length = length;
    }
   
    // ---------------------- Compare By Word ---------------------
    @Override
    public int compareTo(LexiconEntry b){
        return this.word.compareTo(b.word);
    }
    
    // ------------------------- Get Word -----------------------
    public String getWord() {
    	return word;
    }
    
    // ------------------------- Get Offset ----------------------
    public Long getOffset() {
    	return offset;
    }
    
    // ------------------------ Get Length ------------------
    public Long length() {
    	return length;
    }
}