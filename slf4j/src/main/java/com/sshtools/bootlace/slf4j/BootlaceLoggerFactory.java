package com.sshtools.bootlace.slf4j;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;


public class BootlaceLoggerFactory implements ILoggerFactory {
	
	public interface Intercept extends Closeable {
		@Override
		void close();
		
		OutputStream sysOut();
		
		OutputStream sysErr();
		
		InputStream sysIn();
	}

    private static class InterceptedInputStream extends FilterInputStream {

		private final InputStream original;

		public InterceptedInputStream(InputStream originalSysin) {
			super(originalSysin);
			this.original = originalSysin;
		}

		public void intercept(InputStream in) {
			if(in == null) {
				this.in = original;
			}
			else {
				this.in = in;
			}
		}

	}

	private static class InterceptedPrintStream extends FilterOutputStream {

		private final OutputStream original;

		public InterceptedPrintStream(OutputStream out) {
			super(out);
			this.original = out;
		}

		public void intercept(OutputStream out) {
			if(out == null) {
				this.out = original;
			}
			else {
				this.out = out;
			}
		}

	}

	private static ILoggerFactory iLoggerFactory;
    
	private static PrintStream originalSysout;
	private static PrintStream originalSyserr;
	private static InputStream originalSysin;
	
	private static InterceptedPrintStream interceptedSysout;
	private static InterceptedPrintStream interceptedSyserr;
	private static InterceptedInputStream interceptedSysin;

	private static Intercept intercept;
    
    static {
		/* Take over the system streams before logging is initialized. This
		 * means we can take over the console (i.e. plaster-console) later
		 * on and no more logging will appear on the console. It will reappear
		 * if the console is uninstalled */
    	originalSysout = System.out;
    	originalSyserr = System.err;
    	originalSysin = System.in;
		
    	System.setOut(new PrintStream(interceptedSysout =  new InterceptedPrintStream(originalSysout)));	
    	System.setErr(new PrintStream(interceptedSyserr = new InterceptedPrintStream(originalSyserr)));	
    	System.setIn(interceptedSysin = new InterceptedInputStream(originalSysin));
    	
    }
    
    public BootlaceLoggerFactory() {
    }
    
    public static Intercept interceptConsole(OutputStream out, OutputStream err, InputStream in) {
    	if(intercept == null) {
	    	intercept = new Intercept() {
	    		
	    		{
	    			interceptedSyserr.intercept(err); 
	    			interceptedSysout.intercept(out); 
	    			interceptedSysin.intercept(in);
	    		}
				
				@Override
				public void close() {
	    			interceptedSyserr.intercept(null); 
	    			interceptedSysout.intercept(null); 
	    			interceptedSysin.intercept(null);
				}

				@Override
				public OutputStream sysOut() {
					return interceptedSysout.original;
				}

				@Override
				public OutputStream sysErr() {
					return interceptedSyserr.original;
				}

				@Override
				public InputStream sysIn() {
					return interceptedSysin.original;
				}
			};
			return intercept;
    	}
    	else {
    		throw new IllegalStateException("Already intercepting console.");
    	}
    }

    public Logger getLogger(String name) {
    	if(iLoggerFactory == null) {
    		throw new IllegalStateException("Logging implementation must be set before first call to SLF4J.");
    	}
    	return iLoggerFactory.getLogger(name);
    }

	public static void setLogger(ILoggerFactory iLoggerFactory) {
		BootlaceLoggerFactory.iLoggerFactory = iLoggerFactory;
	}
}
