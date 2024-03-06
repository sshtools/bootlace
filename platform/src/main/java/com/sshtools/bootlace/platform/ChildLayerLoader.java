package com.sshtools.bootlace.platform;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import com.sshtools.bootlace.api.ChildLayer;
import com.sshtools.bootlace.api.LayerContext;
import com.sshtools.bootlace.api.Logs;
import com.sshtools.bootlace.api.Logs.BootLog;
import com.sshtools.bootlace.api.Logs.Log;

@Deprecated
/**
 * Is this actually needed if we always bind resources (not a bad idea?)
 */
public final class ChildLayerLoader extends ClassLoader {
	private final class CompoundEnumeration implements Enumeration<URL> {
		private final Enumeration<URL> res;
		private final Enumeration<URL> superRes;

		private CompoundEnumeration(Enumeration<URL> res, Enumeration<URL> superRes) {
			this.res = res;
			this.superRes = superRes;
		}

		@Override
		public URL nextElement() {
			if (res.hasMoreElements())
				return res.nextElement();
			else
				return superRes.nextElement();
		}

		@Override
		public boolean hasMoreElements() {
			return res.hasMoreElements() || superRes.hasMoreElements();
		}
	}

	private final static Log LOG = Logs.of(BootLog.LOADING);

	private final String name;
	private ThreadLocal<Boolean> defeatFindResource = new ThreadLocal<>();
	private ThreadLocal<Boolean> defeatGetResource = new ThreadLocal<>();
	private ModuleLayer moduleLayer;
	private ChildLayer layer;

	ChildLayerLoader(ChildLayer layer, ClassLoader systemClassLoader) {
		super(systemClassLoader);
		this.layer = layer;

		name = String.format("ChildLayerLoader-%s", layer.id());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		if (LOG.debug())
			LOG.debug("loadClass(''{0}'')", name);

		return super.loadClass(name);
	}

	@Override
	public URL getResource(String name) {
		if (!Boolean.TRUE.equals(defeatGetResource.get())) {
			/* This is where child first happens */
			var url = findResource(name);
			if (url == null) {
				defeatFindResource.set(true);
				try {
					url = super.getResource(name);
				} finally {
					defeatFindResource.set(false);
				}
			}
			return url;
		} else
			return null;
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		var res = findResources(name);
		defeatFindResource.set(true);
		try {
			return new CompoundEnumeration(res, super.getResources(name));
		} finally {
			defeatFindResource.set(false);
		}
	}

	@Override
	protected URL findResource(String name) {

		if (!Boolean.TRUE.equals(defeatFindResource.get())) {

			var thisLayerCtx = LayerContext.get(moduleLayer);

			if (LOG.debug())
				LOG.debug("getResource(''{0}'') in {1} ({2})", name, hashCode(), thisLayerCtx.layer().id());

			for (var child : thisLayerCtx.childLayers()) {
				var it = child.modules().iterator();
				
				
				URL url = null;
				while (it.hasNext()) {
					var clayer = it.next();
					
					if (LOG.debug())
						LOG.debug("    Module {0}", clayer.getName());
				
					var aloader = clayer.getLayer().findLoader(clayer.getName());
					
					
					defeatGetResource.set(true);
					try {
						url = aloader.getResource(name);
					} finally {
						defeatGetResource.set(false);
					}

					if (url != null) {
						return url;
					}
				}
			}

			var url = super.findResource(name);
			if (url != null)
				return url;
		}

		return null;
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {

		if (!Boolean.TRUE.equals(defeatFindResource.get())) {

			if (LOG.debug())
				LOG.debug("findResources(''{0}'')", name);

			Enumeration<URL> first = null;

			for (var child : LayerContext.get(moduleLayer).childLayers()) {

				var ctx = LayerContext.get(child);
				var second = ((ChildLayerLoader) ctx.loader()).findResources(name);

				if (second != null) {
					if (first == null) {
						first = second;
					} else {
						first = new CompoundEnumeration(first, second);
					}
				}
			}

			if (first != null) {
				return first;
			}
		}

		return super.findResources(name);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (LOG.debug())
			LOG.debug("loadClass(''{0}'', ''{1}'')", name, resolve);
		return super.loadClass(name, resolve);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (LOG.debug())
			LOG.debug("findClass(''{0}'')", name);

		return super.findClass(name);
	}

	@Override
	protected Class<?> findClass(String moduleName, String name) {

		if (LOG.debug())
			LOG.debug("findClass(''{0}'', ''{1}'')", moduleName, name);

		return super.findClass(moduleName, name);
	}

	void module(ModuleLayer moduleLayer) {
		this.moduleLayer = moduleLayer;
	}

}
