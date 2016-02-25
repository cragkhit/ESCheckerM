package elasticsearch;

public class HitEntry {
	private String file;
	private Double frequency;
	
	public HitEntry(String file, Double frequency) {
		this.file = file;
		this.frequency = frequency;
	}

	public String getFile() {
		return file;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public Double getFrequency() {
		return frequency;
	}

	public void setFrequency(Double frequency) {
		this.frequency = frequency;
	}
}
