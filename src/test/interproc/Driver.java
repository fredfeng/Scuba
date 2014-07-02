package test.interproc;

import java.util.ListIterator;


public class Driver {
	
	A f;

//	Hashtable ht = new Hashtable();
	

	
	

	public static void main(String[] args) {
//		String a = new String("hi");
//		Set set = new HashSet();
//		set.add(a);
//		Iterator it = set.iterator();
//		while(it.hasNext()) {
//			String s = (String)it.next();
//		}
		
//		A a = new A();
//		Hashtable ht = new Hashtable();
//		ht.put(1, a);
//		
//		
//		A b = (A)ht.get(1);
		
		//isil's example
//		Driver d = new Driver();
//		  d.f = new A();
//		  d.f.test1();
		//my example
//		List<C> l = new LinkedList<C>();
//		l.add(new C());
//		new C(l);
		
		A y = new Driver().run();
        
//		String b = (String)t.get(1);
//		TreeSet m = new TreeSet();
//		m.add("a");
//		TreeSet m2 = new TreeSet();
//		m2.addAll(m);
	}
	
	public A run() {
//		HashMap ht = new HashMap();
//		ht.put(1, new String("hi"));
//		v.in
		
//		Hashtable ht2 = new Hashtable();
//		ht2.putAll(ht);

//		String s = (String) ht.get(1);
		
//		String b = (String) v.get(1);
//		A[] arr = new A[2];
//		arr[1] = new A();
//		
//		B b = (B) arr[1];
//		foo();
		
        DoubleLinkedList<A> list = new DoubleLinkedList<A>();
        
        list.add(new A());
        list.add(new A());
        list.add(new A());

        ListIterator<A> iterator = list.iterator();
//        A x = null;
//        if (iterator.hasNext()) {
          A  x = iterator.next();
//            iterator.previous();
//            iterator.remove();
//            iterator.add(x);
//            iterator.set(x);
//        }
        return x;
	}
	
//	public String[] foo() {
//		String[] a = new String[1];
//		return a;
//	}
//	
//	public Object get(int i) {
//		return ht.get(i);
//	}
//	
//	public void add(int i, Object o) {
//		ht.put(i, o);
//	}
}
