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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sshtools.bootlace.api.Layer;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;

public final class LayerContextImpl implements LayerContext {

	public final static class Provider implements LayerContext.Provider {
		private final static Log LOG = Logs.of(BootLog.LAYERS);

		@Override
		public LayerContext get(ModuleLayer layer) {
			return new LayerContextImpl(layer);
		}
		
	}

	private final ModuleLayer moduleLayer;
	
	private LayerContextImpl(ModuleLayer moduleLayer) {
		this.moduleLayer = moduleLayer;
	}
	
	private final static Map<String, ModuleLayer> layers = new ConcurrentHashMap<>();
	private final static Map<ModuleLayer, Layer> layerDefs = new ConcurrentHashMap<>();
	private final static Map<ModuleLayer, ClassLoader> loaders = new ConcurrentHashMap<>();

	static void register(ModuleLayer layer, Layer layerDef, ClassLoader loader) {
		Provider.LOG.info("Registering layer for context `{0}`", layerDef.id());

		loaders.put(layer, loader);
		layers.put(layerDef.id(), layer);
		layerDefs.put(layer, layerDef);
		
		Provider.LOG.info("Registered layer for context `{0}`", layerDef.id());
	}
	
	@Override
	public Layer layer() {
		return layerDefs.get(moduleLayer);
	}

	@Override
	public ClassLoader loader() {
		return loaders.get(moduleLayer);
	}
	
	@Override
	public Set<Layer> parents() {
		return Collections.unmodifiableSet(addParents(new LinkedHashSet<>(), moduleLayer));
	}
	
	private Set<Layer> addParents(Set<Layer> l, ModuleLayer layer) {
		var cl = new LinkedHashSet<LayerContextImpl>();
		layer.parents().forEach(p -> {
			LayerContextImpl ctx = ((LayerContextImpl)LayerContext.get(p));
			cl.add(ctx);
			l.add(ctx.layer());
		});
		cl.forEach(ctx -> {
			addParents(l, ctx.moduleLayer);
		});
		return l;
	}

	public static void deregister(String id, ModuleLayer moduleLayer) {
		layerDefs.remove(moduleLayer);;
		loaders.remove(moduleLayer);
		layers.remove(id);
	}

	@Override
	public Iterable<ModuleLayer> childLayers() {
		var ch = new LinkedHashSet<ModuleLayer>();
		for(var l : layers.values()) {
			if(l.parents().contains(moduleLayer)) {
				ch.add(l);
			}
		}
		return Collections.unmodifiableSet(ch);
	}
}
