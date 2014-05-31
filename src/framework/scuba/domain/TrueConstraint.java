package framework.scuba.domain;

public class TrueConstraint extends Constraint {
	
	final String id = "TRUE";
	
	@Override
	public String toString() {
		return id;
	}
	
	public Constraint clone() {
		return new TrueConstraint();
	}
}
