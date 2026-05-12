package com.sshtools.bootlace.api;

import java.nio.file.Path;

/**
 * Tag interface for layers that are loaded from the local filesystem.
 */
public interface LocalLayer extends ExtensionLayer {
	
	Path directory();
	
	Path readDirectory();
	
	Path writeDirectory();

	default boolean isSingleDir() {
		return readDirectory().toString().equals(writeDirectory().toString());
	}
}
