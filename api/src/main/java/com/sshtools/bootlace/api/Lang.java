package com.sshtools.bootlace.api;

import java.util.concurrent.Callable;

public class Lang {

	public static void runWithoutLoader(Runnable r) {
		runWithLoader((Class<?>)null, r);
	}

	public static void runWithLoader(Class<?> clazz, Runnable r) {
		runWithLoader(clazz == null ? (ClassLoader)null : clazz.getClassLoader(), r);
	}

	public static void runWithLoader(ClassLoader loader, Runnable r) {
		var was = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(loader);
			r.run();
		}
		finally {
			Thread.currentThread().setContextClassLoader(was);
		}
	}
	
	public static <V> V callWithLoader(Class<?> clazz, Callable<V> r) throws Exception {
		return callWithLoader(clazz == null? null : clazz.getClassLoader(), r);
	}
	
	public static <V> V callWithLoader(ClassLoader loader, Callable<V> r) throws Exception {
		var was = Thread.currentThread().getContextClassLoader();
		try {
			System.out.println("Using .. " + loader);
			Thread.currentThread().setContextClassLoader(loader);
			return r.call();
		}
		finally {
			Thread.currentThread().setContextClassLoader(was);
		}
	}
}
