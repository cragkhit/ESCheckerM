package elasticsearch;

public class HitEntry {
	private String file;
	private int frequency;
	
	public HitEntry(String file, int frequency) {
		this.file = file;
		this.frequency = frequency;
	}

	public String getFile() {
		return file;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public int getFrequency() {
		return frequency;
	}

	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}
}
