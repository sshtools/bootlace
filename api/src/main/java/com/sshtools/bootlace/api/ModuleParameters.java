package com.sshtools.bootlace.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public interface ModuleParameters {
	
	Set<String> nativeModules();
	
	public static abstract class AbstractModuleParameters implements ModuleParameters {

		protected final Set<String> nativeModules;

		protected AbstractModuleParameters(AbstractModuleParametersBuilder<?,?> builder) {
			this.nativeModules = Collections.unmodifiableSet(new LinkedHashSet<>(builder.nativeModules));
		}

		@Override
		public Set<String> nativeModules() {
			return nativeModules;
		}
	}
	

	public abstract static class AbstractModuleParametersBuilder<BLDR extends AbstractModuleParametersBuilder<BLDR, PRMS>, PRMS extends ModuleParameters> {

		Set<String> nativeModules = new LinkedHashSet<>();


		public final BLDR addNativeModules(String... nativeModules) {
			return addNativeModules(Arrays.asList(nativeModules));
		}

		@SuppressWarnings("unchecked")
		public final BLDR addNativeModules(Collection<String> nativeModules) {
			this.nativeModules.addAll(nativeModules);
			return (BLDR)this;
		}

		public final BLDR withNativeModules(Collection<String> nativeModules) {
			this.nativeModules.clear();
			return addNativeModules(nativeModules);
		}

		public final BLDR withNativeModules(String... nativeModules) {
			return withNativeModules(Arrays.asList(nativeModules));
		}
		
		public abstract PRMS build();
	}
}
