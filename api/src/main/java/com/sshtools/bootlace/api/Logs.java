package com.sshtools.bootlace.api;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Tiny logging framework that uses {@link System#getLogger(String)} underneath,
 * but with a more concise syntax.
 * <p>
 * All native bootlace components (and the Jenny framework) will uses this, as
 * can applications based on these. This will allow concrete applications to
 * choose their logging framework with other the framework interfering.
 * <p>
 * The bootstrap module of such apps can then configure or adapt JUL to suit
 * their cases, and any logging from the framework will also be captured and can
 * be processed using known categories.
 * 
 */
public class Logs {

	@FunctionalInterface
	public interface Category {
		String name();

		static Category ofSimpleName(Class<?> clazz) {
			return new Category() {
				
				@Override
				public String name() {
					return clazz.getSimpleName();
				}
			};
		}
		
		static Category ofName(Class<?> clazz) {
			return new Category() {
				
				@Override
				public String name() {
					return clazz.getName();
				}
			};
		}
		
		static Category ofName(String name) {
			return new Category() {
				
				@Override
				public String name() {
					return name;
				}
			};
		}
	}

	public enum BootLog implements Category {
		RESOLUTION, LOADING, LAYERS, ZIP, HTTP, MODULES
	}

	public final static class Log {

		private final Logger log;

		private Log(Category category) {
			log = System.getLogger(category.name());
		}

		private Log(Category category, ResourceBundle resources) {
			log = System.getLogger(category.name(), resources);
		}

		public boolean debug() {
			return log.isLoggable(Level.DEBUG);
		}

		public Log whenDebug(Runnable r) {
			if (debug())
				r.run();
			return this;
		}

		public Log debug(String msg) {
			log.log(Level.DEBUG, msg);
			return this;
		}

		public Log debug(Supplier<String> msgSupplier) {
			log.log(Level.DEBUG, msgSupplier);
			return this;
		}

		public Log debug(Object obj) {
			log.log(Level.DEBUG, obj);
			return this;
		}

		public Log debug(String msg, Throwable thrown) {
			log.log(Level.DEBUG, msg, thrown);
			return this;
		}

		public Log debug(Supplier<String> msgSupplier, Throwable thrown) {
			log.log(Level.DEBUG, msgSupplier, thrown);
			return this;
		}

		public Log debug(String format, Object... params) {
			log.log(Level.DEBUG, format, params);
			return this;
		}

		public Log debug(ResourceBundle bundle, String msg, Throwable thrown) {
			log.log(Level.DEBUG, bundle, msg, thrown);
			return this;
		}

		public Log debug(Level level, ResourceBundle bundle, String format, Object... params) {
			log.log(Level.DEBUG, bundle, format, params);
			return this;
		}

		public boolean error() {
			return log.isLoggable(Level.ERROR);
		}

		public Log error(String msg) {
			log.log(Level.ERROR, msg);
			return this;
		}

		public Log error(Supplier<String> msgSupplier) {
			log.log(Level.ERROR, msgSupplier);
			return this;
		}

		public Log error(Object obj) {
			log.log(Level.ERROR, obj);
			return this;
		}

		public Log error(String msg, Throwable thrown) {
			log.log(Level.ERROR, msg, thrown);
			return this;
		}

		public Log error(Supplier<String> msgSupplier, Throwable thrown) {
			log.log(Level.ERROR, msgSupplier, thrown);
			return this;
		}

		public Log error(String format, Object... params) {
			log.log(Level.ERROR, format, params);
			return this;
		}

		public Log error(ResourceBundle bundle, String msg, Throwable thrown) {
			log.log(Level.ERROR, bundle, msg, thrown);
			return this;
		}

		public Log error(Level level, ResourceBundle bundle, String format, Object... params) {
			log.log(Level.ERROR, bundle, format, params);
			return this;
		}

		public Log info(String msg) {
			log.log(Level.INFO, msg);
			return this;
		}

		public Log info(Supplier<String> msgSupplier) {
			log.log(Level.INFO, msgSupplier);
			return this;
		}

		public Log info(Object obj) {
			log.log(Level.INFO, obj);
			return this;
		}

		public Log info(String msg, Throwable thrown) {
			log.log(Level.INFO, msg, thrown);
			return this;
		}

		public Log info(Supplier<String> msgSupplier, Throwable thrown) {
			log.log(Level.INFO, msgSupplier, thrown);
			return this;
		}

		public Log info(String format, Object... params) {
			log.log(Level.INFO, format, params);
			return this;
		}

		public Log info(ResourceBundle bundle, String msg, Throwable thrown) {
			log.log(Level.INFO, bundle, msg, thrown);
			return this;
		}

		public Log info(Level level, ResourceBundle bundle, String format, Object... params) {
			log.log(Level.INFO, bundle, format, params);
			return this;
		}

		public boolean info() {
			return log.isLoggable(Level.INFO);
		}

		public Log warning(String msg) {
			log.log(Level.WARNING, msg);
			return this;
		}

		public Log warning(Supplier<String> msgSupplier) {
			log.log(Level.WARNING, msgSupplier);
			return this;
		}

		public Log warning(Object obj) {
			log.log(Level.WARNING, obj);
			return this;
		}

		public Log warning(String msg, Throwable thrown) {
			log.log(Level.WARNING, msg, thrown);
			return this;
		}

		public Log warning(Supplier<String> msgSupplier, Throwable thrown) {
			log.log(Level.WARNING, msgSupplier, thrown);
			return this;
		}

		public Log warning(String format, Object... params) {
			log.log(Level.WARNING, format, params);
			return this;
		}

		public Log warning(ResourceBundle bundle, String msg, Throwable thrown) {
			log.log(Level.WARNING, bundle, msg, thrown);
			return this;
		}

		public Log warning(Level level, ResourceBundle bundle, String format, Object... params) {
			log.log(Level.WARNING, bundle, format, params);
			return this;
		}

		public boolean warning() {
			return log.isLoggable(Level.WARNING);
		}

		public Log trace(String msg) {
			log.log(Level.TRACE, msg);
			return this;
		}

		public Log trace(Supplier<String> msgSupplier) {
			log.log(Level.TRACE, msgSupplier);
			return this;
		}

		public Log trace(Object obj) {
			log.log(Level.TRACE, obj);
			return this;
		}

		public Log trace(String msg, Throwable thrown) {
			log.log(Level.TRACE, msg, thrown);
			return this;
		}

		public Log trace(Supplier<String> msgSupplier, Throwable thrown) {
			log.log(Level.TRACE, msgSupplier, thrown);
			return this;
		}

		public Log trace(String format, Object... params) {
			log.log(Level.TRACE, format, params);
			return this;
		}

		public Log trace(ResourceBundle bundle, String msg, Throwable thrown) {
			log.log(Level.TRACE, bundle, msg, thrown);
			return this;
		}

		public Log trace(Level level, ResourceBundle bundle, String format, Object... params) {
			log.log(Level.TRACE, bundle, format, params);
			return this;
		}
		
		public boolean trace() {
			return log.isLoggable(Level.TRACE);
		}
	}

	public static Log of(Category category) {
		return new Log(category);
	}

	public static Log of(Category category, ResourceBundle resources) {
		return new Log(category, resources);
	}

}
