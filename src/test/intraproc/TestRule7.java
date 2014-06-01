package test.intraproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Test case for complicated control flows.
 * 
 * @author yufeng
 * 
 */
public class TestRule7 {

	class Z {
		G g;
	}
	
	class G {
		G g = new G();
	}
	
	class X {
		Z z;
	}

	class Y extends X {
	}
	
	// Complicated control flow.
	void foo(Z a) {
		int i = 0;
		Y y = new Y();
		X x = new X();
		
		Z z = new Z();
		y.z = z;
		x = y;
		x.z.g = new G();
		X x1 = x;
		List<G> list = new ArrayList();
		
		G g = x1.z.g;
		G g1 = x1.z.g.g;
		if(i > 0){
			list.add(z.g);
			list.add(x.z.g);
			list.add(x.z.g.g);
			list.add(x1.z.g.g);
			for(G mg : list) {
				if(mg != null)
					g = mg;
			}
		} else {
			g = new G();
		}
		
	}
}
