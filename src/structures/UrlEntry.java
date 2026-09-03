// Used to easily store URL-ID-Score pairs
package structures;

public class UrlEntry implements Comparable<UrlEntry>{
	// Unitialized variables
	private String url;
	private String id;
	private double score;
	
	// ------------------------- Constructor ------------------------
	public UrlEntry(String url, String id, double score) {
		this.url = url;
		this.id = id;
		this.score = score;
	}
	
	// -------------------------- toString -----------------------------
	public String toString() {
		return url + "\n" + id + "\nSCORE:: " + score;
	}
	
	// ----------------------------- Score Comparison -------------------------
	
	// Compares UrlEntries by their scores
	@Override
	public int compareTo(UrlEntry other) {
		if(this.score > other.score) {
			return 1;
		}
		else if (this.score == other.score) {
			return 0;
		}
		return -1;
	}
	
	// ---------------------------------- Getter Methods ----------------------------
	public String getUrl() {
		return url;
	}
	
	public String getId() {
		return id;
	}
	
	public double getScore() {
		return score;
	}
	
	// ---------------------------------- Setter Methods ---------------------------
	public void setUrl(String newUrl) {
		url = newUrl;
	}
	
	public void setId(String newId) {
		id = newId;
	}
	
	public void setScore(double newScore) {
		score = newScore;
	}
	
	// ------------------------------- URL Comparison ---------------------------------
	
	// Compares UrlEntries by their URL
	@Override
	public boolean equals(Object o) {
		// Compare references
		if (this == o) return true;
		
		// Compare classes
		if(o == null || o.getClass() != getClass()) return false;
		
		// Convert object to UrlEntry once established that it is a UrlEntry
		UrlEntry other = (UrlEntry) o;
		
		// Compare URLs
		if(other.url.equals(this.url)) {
			return true;
		}
		
		return false;
	}
	
	// -------------------------------- Hash Code -------------------------------
	@Override
	public int hashCode() {
		return this.url.hashCode();
	}
}