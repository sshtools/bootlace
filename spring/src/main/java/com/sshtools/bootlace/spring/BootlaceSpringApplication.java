package com.sshtools.bootlace.spring;

import java.util.HashMap;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.sshtools.bootlace.api.BootContext;
import com.sshtools.bootlace.api.Plugin;
import com.sshtools.bootlace.platform.Bootlace;

public class BootlaceSpringApplication {

	public static void main(String[] args) {
		try (var context = new ClassPathXmlApplicationContext()) {

			var pluginToCtx = new HashMap<Plugin, AnnotationConfigApplicationContext>();
			Bootlace.build()
				.fromStandardArguments(args)
				.withContext(BootContext.named("springtest"))
				.withPluginInitialiser((prov, layer, parents) -> {
					/*
					 * Instead of ServiceProvider.get(), we use Spring to load type `type`.
					 */
					var type = prov.type();

					var beansResource = type.getResource("plugin-beans.xml");
					if (beansResource == null) {
						@SuppressWarnings("resource")
						var ctx = new AnnotationConfigApplicationContext();
						ctx.setParent(context);
						ctx.setClassLoader(type.getClassLoader());
						ctx.register(type);
						ctx.scan(type.getPackage().getName());
						ctx.refresh();
						ctx.start();
						return ctx.getBean(Plugin.class);
					} else {
						@SuppressWarnings("resource")
						var ctx = new ClassPathXmlApplicationContext(new String[] { "plugin-beans.xml" }, type);
						ctx.setParent(context);
						ctx.setClassLoader(type.getClassLoader());
						ctx.refresh();
						ctx.start();
						return ctx.getBean(Plugin.class);
					}
				}).withPluginDestroyer((plugin, layer) -> {
					var ctx = pluginToCtx.remove(plugin);
					// TODO other cleanup?
					ctx.stop();
				}).build();

		}
	}
}
