package test.intraproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Test case for list iterator.
 *
 */
public class TestRule15 {
	
	class X {
	}
	
	
	//a = v2.
	void foo(X a, X b) {
        List<X> list = new ArrayList();
        X x = new X();
        list.add(new X());
        list.add(new X());
        list.add(new X());
        list.add(new X());
        list.add(x);
        X y;
        Iterator<X> it = list.iterator();
        while(it.hasNext()) {
        	y = it.next();
        	X z = y;
        }
	}
}
