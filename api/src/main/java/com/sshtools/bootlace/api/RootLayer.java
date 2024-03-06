package com.sshtools.bootlace.api;

import java.net.URL;
import java.util.Optional;

public interface RootLayer extends Layer {

	void waitFor();

//	void afterOpen(ChildLayer child);
//
//	void beforeClose(ChildLayer layer);
//
//	void close(ChildLayer layer);
//
//	void open(ChildLayer layerDef);
	
	ChildLayer getLayer(String id);
	
	Optional<URL> globalResource(String path);

}