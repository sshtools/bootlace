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
package com.sshtools.bootlace.platform;

import java.nio.file.Path;

import com.sshtools.bootlace.api.ExtensionLayer;

/**
 * A Container layer that watches a particular directory to load further layers
 * from.
 */
public final class StaticLayerImpl extends AbstractStaticLayer implements ExtensionLayer {

	public final static class Builder extends AbstractStaticLayerBuilder<Builder> {

		public Builder(String id) {
			super(id);
		}

		public StaticLayerImpl build() {
			return new StaticLayerImpl(this);
		}
	}


	private StaticLayerImpl(Builder builder) {
		super(builder);
	}
	
	@Override
	protected Path defaultDirectory() {
		throw new IllegalStateException("Static layers required directory is specified.");
	}

}
