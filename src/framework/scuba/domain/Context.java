package framework.scuba.domain;

import java.util.LinkedList;

public class Context {

	protected LinkedList<ProgramPoint> context;

	public Context() {

	}

	public Context(ProgramPoint point) {
		this.context = new LinkedList<ProgramPoint>();
		this.context.add(point);
	}

	public void appendFront(ProgramPoint point) {

		this.context.addFirst(point);

	}

	public void appendEnd(ProgramPoint point) {

		this.context.addLast(point);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[Context]");
		for (ProgramPoint point : context) {
			sb.append(point + "@");
		}
		return sb.toString();
	}
}
