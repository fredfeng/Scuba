package framework.scuba.domain;

// this class implements the edge numbering
public class Numbering {

	protected int number;

	protected boolean inSCC;

	public Numbering(int number, boolean inSCC) {
		this.number = number;
		this.inSCC = inSCC;
	}

	public int getNumber() {
		return number;
	}

	public boolean isInSCC() {
		return inSCC;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof Numbering)
				&& (inSCC == ((Numbering) other).inSCC)
				&& (number == ((Numbering) other).number);
	}

	@Override
	public int hashCode() {
		return 37 * number + (inSCC ? 1 : 0);
	}

	@Override
	public String toString() {
		return number + "|" + inSCC;
	}
}