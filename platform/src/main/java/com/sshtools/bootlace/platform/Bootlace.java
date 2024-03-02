package com.sshtools.bootlace.platform;


import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;

public class Bootlace {
	
	public static RootLayerBuilder build() {
		return new RootLayerBuilder();
	}
	
	public static RootLayerBuilder build(String id) {
		return new RootLayerBuilder(id);
	}

	static INIReader.Builder createINIReader() {
		return new INIReader.Builder().
				withMultiValueMode(MultiValueMode.SEPARATED).
				withDuplicateKeysAction(DuplicateAction.APPEND);
	}

}
