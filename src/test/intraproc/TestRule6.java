package test.intraproc;


/**
 * Test case for Rule 4 in figure 8.
 * @author yufeng
 *
 */
public class TestRule6 {
	
	class Z {
	}
	
	//if-else.
	void foo(Z a) {
		int i = 1;
		Z v,u;
		if(i > 0)	
			v = new Z();
		else
			u = new Z();
	}
}
