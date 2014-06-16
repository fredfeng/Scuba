package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;

import com.microsoft.z3.BoolExpr;

public class AliasQueries {
	// the method this alias query belongs in
	final protected jq_Method method;
	// the summary this alias query belongs in
	final protected Summary summary;

	// alias query mapping from alias query element to constraints
	final protected Map<AliasQueryElem, BoolExpr> queries;

	public AliasQueries(jq_Method method, Summary summary) {
		this.method = method;
		this.summary = summary;
		this.queries = new HashMap<AliasQueryElem, BoolExpr>();
	}

	public jq_Method getMethod() {
		return method;
	}

	public AbstractHeap getAbsHeap() {
		return summary.getAbsHeap();
	}

	public Map<AliasQueryElem, BoolExpr> getQueries() {
		return queries;
	}

	public boolean handleAliasQueryStmt() {
		boolean ret = false;
		
		return ret;
	}
}
