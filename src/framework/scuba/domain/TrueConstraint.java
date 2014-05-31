package framework.scuba.domain;

public class TrueConstraint extends Constraint {
	@Override
	public String toString() {
		return "TRUE";
	}
	
	public Constraint clone() {
		return new TrueConstraint();
	}
}