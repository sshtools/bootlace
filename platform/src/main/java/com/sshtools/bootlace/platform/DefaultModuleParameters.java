package com.sshtools.bootlace.platform;

import java.util.Arrays;
import java.util.Optional;

import com.sshtools.bootlace.api.ModuleParameters;
import com.sshtools.bootlace.api.ModuleParameters.AbstractModuleParameters;
import com.sshtools.bootlace.platform.jini.INI.Section;

public class DefaultModuleParameters extends AbstractModuleParameters {

	private DefaultModuleParameters(DefaultModuleParametersBuilder bldr) {
		super(bldr);
	}

	public final static class DefaultModuleParametersBuilder extends AbstractModuleParametersBuilder<DefaultModuleParametersBuilder, DefaultModuleParameters> {

		@Override
		public DefaultModuleParameters build() {
			return new  DefaultModuleParameters(this);
		}

		public DefaultModuleParametersBuilder fromModuleSection(Section section) {
			addNativeModules(Arrays.asList(section.getAllOr("native").orElse(new String[0])));
			return this;
		}

		public DefaultModuleParametersBuilder fromParameters(ModuleParameters moduleParameters) {
			addNativeModules(moduleParameters.nativeModules());
			return this;
		}

		public DefaultModuleParametersBuilder fromParameters(Optional<ModuleParameters> moduleParameters) {
			moduleParameters.ifPresent(this::fromParameters);
			return this;
		}
		
	}
}
