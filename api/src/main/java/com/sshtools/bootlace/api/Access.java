/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.bootlace.api;

/**
 * Determines if or how a {@link Plugin} in a particular layer may access other
 * layers by their ID through {@link PluginContext#layer(String)}.
 * <p>
 * Note, when a {@link Layer} is prevent access bv this flag, classes and parent
 * in parents layers may still be accessed, but it will not be possible to
 * access the {@link Layer} object to obtain further information or layers.
 */
public enum Access {
	/**
	 * The layer may be accessed from anywhere by it's <strong>unique</strong> ID.
	 */
	PUBLIC,
	/**
	 * The layer be accessed if it is a direct parent of another.
	 */
	PROTECTED,
	/**
	 * The layer may not be accessed at all by anything other than other plugins in
	 * the same layer.
	 */
	PRIVATE
}
