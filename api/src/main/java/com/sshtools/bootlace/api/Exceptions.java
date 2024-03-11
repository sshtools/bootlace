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

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;

/**
 * Various exceptions that might occur while interacting with Bootlace.
 */
public class Exceptions {

	@SuppressWarnings("serial")
	public final static  class FailedToOpenPlugin extends RuntimeException {
		public FailedToOpenPlugin(PluginRef plugin) {
			
		}
		public FailedToOpenPlugin(PluginRef plugin, Throwable cause) {
			super(MessageFormat.format("The plugin ''{0}'' failed to load.", plugin.plugin().getClass().getName()), cause);
		}
	}
	
	@SuppressWarnings("serial")
	public final static  class FailedToClosePlugin extends RuntimeException {
		public FailedToClosePlugin(PluginRef plugin) {
			
		}
		public FailedToClosePlugin(PluginRef plugin, Throwable cause) {
			super(MessageFormat.format("The plugin ''{0}'' failed to close cleanly.", plugin.plugin().getClass().getName()), cause);
		}
	}

	@SuppressWarnings("serial")
	public final static class MissingPluginClassException extends RuntimeException {
		
		public MissingPluginClassException(Class<? extends Plugin> clazz) {
			super(MessageFormat.format(
					"""
					
					
					The plugin ''{0}'' has been discovered by ServiceLoader, but the module that contains it has not registered it.
					The most likely fix is to add ''provides {1} with {2}'' to the module-info.java in the same artifact.
					
					It might also mean you are trying to access sibling plugins in a plugin's constructor (or in an initialised member variable).
					This is not currently possible. Instead, you should access them in your plugings afterOpen() method, when the
					plugin instances will exist. Alternatively, you could attach them to a different (higher) layer.
					""", clazz.getName(), Plugin.class.getName(), clazz.getName()));
		}
	}
	
	@SuppressWarnings("serial")
	public final static class NotALayer extends RuntimeException {
		
		public NotALayer(Path path) {
			super(MessageFormat.format("The artifact ''{0}'' exists, but it does not contain a layers.ini resource, suggesting it is not a plugin.", path));
		}
	}
	
	@SuppressWarnings("serial")
	public final static class InvalidUsernameOrPasswordException extends RuntimeException {
		
		private final String username;
		
		public InvalidUsernameOrPasswordException(String username) {
			this(username, null);
		}
		
		public InvalidUsernameOrPasswordException(String username, Throwable cause) {
			super("Invalid credentials, username or password incorrect.", cause);
			this.username = username;
		}
		
		public String username() {
			return username;
		}
	}
	
	@SuppressWarnings("serial")
	public final static class InvalidCredentials extends RuntimeException {
		
		private final Optional<String> username;
		
		public InvalidCredentials() {
			this(Optional.empty());
		}
		
		public InvalidCredentials(Optional<String> username) {
			this(username, null);
		}
		
		public InvalidCredentials(Optional<String> username, Throwable cause) {
			super("Invalid credentials.", cause);
			this.username = username;
		}
		
		public Optional<String> username() {
			return username;
		}
	}
}
