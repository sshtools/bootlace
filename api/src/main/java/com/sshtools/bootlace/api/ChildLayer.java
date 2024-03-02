package com.sshtools.bootlace.api;

import java.util.Optional;
import java.util.Set;

public interface ChildLayer extends Layer {

	Optional<RootLayer> appLayer();

	String id();

	Set<String> parents();

	void onAfterOpen();

	void onOpened();

}