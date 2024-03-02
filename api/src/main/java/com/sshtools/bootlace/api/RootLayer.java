package com.sshtools.bootlace.api;

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

}