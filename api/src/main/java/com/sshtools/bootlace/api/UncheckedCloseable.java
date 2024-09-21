package com.sshtools.bootlace.api;

import java.io.Closeable;

public interface UncheckedCloseable extends Closeable {
	
	public static UncheckedCloseable onClose(Runnable r) {
		return new UncheckedCloseable() {
			@Override
			public void close() {
				r.run();
			}
		};
	}

	public static UncheckedCloseable empty() {
		return new UncheckedCloseable() {
			@Override
			public void close() {
			}
		};
	}
	
	@Override
	void close();
}