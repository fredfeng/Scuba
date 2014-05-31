package test.intraproc;

/**
 * Test case for Rule1 in figure 8.
 * @author yufeng
 *
 */
public class TestRule1 {
	
	class Z {
		
	}
	
	//v1 = v2.
	void foo(Z a) {
		Z x = a;
	}
}
