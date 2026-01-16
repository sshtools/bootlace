package com.sshtools.bootlace.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class BootlaceLoggerFactory implements ILoggerFactory {

    private static ILoggerFactory iLoggerFactory;

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
