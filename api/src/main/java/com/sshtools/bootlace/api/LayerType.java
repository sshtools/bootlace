package com.sshtools.bootlace.api;

public enum LayerType {
	/**
	 * Default layer type. May contain child layers, but they will only be
	 * configured when it's parent is configured.
	 */
	DEFAULT,
	/**
	 * Like a dynamic layer, but may not be loaded or removed at run-time, they will only be
	 * configured when it's parent is configured.
	 */
	STATIC,
	/**
	 * May contain dynamic child layers. Layers may be added or removed at run-time.
	 * Each child layer may be a zip file or a directory.
	 */
	DYNAMIC,
	/**
	 * Similar to {@link LayerType#DEFAULT}, but will be treated as if it depends on
	 * all sibling layers, meaning it is loaded last.
	 */
	GROUP,
	/**
	 * Similar to {@link LayerType#DEFAULT}, but just the root layer will be of this
	 * type.
	 */
	ROOT,
	/**
	 * The boot layer may only exist in the root layer but is otherwise similar to
	 * {@link LayerType#DEFAULT}.
	 */
	BOOT

}
