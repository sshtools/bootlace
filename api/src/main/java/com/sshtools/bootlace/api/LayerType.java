package com.sshtools.bootlace.api;

public enum LayerType {
	/**
	 * Default layer type. May contain child layers, but they will only be
	 * configured when it's parent is configure.
	 */
	STATIC,
	/**
	 * May contain dynamic child layers. Layers may be added or removed.
	 */
	DYNAMIC,
	/**
	 * Similar to {@link LayerType#STATIC}, but will be treated as if it depends on
	 * all sibling layers, meaning it is loaded last.
	 */
	GROUP,
	/**
	 * Similar to {@link LayerType#STATIC}, but just the root layer will be of this
	 * type.
	 */
	ROOT,
	/**
	 * The boot layer may only exist in the root layer. Similar to
	 * {@link LayerType#STATIC}.
	 */
	BOOT

}
