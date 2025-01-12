package com.googlecode.objectify.cache;

import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * <p>Dynamic proxy which wraps a MemcacheService and adds retries when an exception occurs.
 * It logs and masks exceptions on complete failure.</p> 
 * 
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
@Slf4j
public class MemcacheServiceRetryProxy implements InvocationHandler
{
	/** */
	private static final int DEFAULT_TRIES = 4;
	
	/**
	 * Create the proxy that does retries. Adds a strict error handler to the service.
	 */
	public static MemcacheService createProxy(MemcacheService raw)
	{
		return createProxy(raw, DEFAULT_TRIES);
	}
	
	/**
	 * Create the proxy that does retries. Adds a strict error handler to the service.
	 */
	public static MemcacheService createProxy(MemcacheService raw, int retryCount) {
		raw.setErrorHandler(ErrorHandlers.getStrict());
		
		return (MemcacheService)java.lang.reflect.Proxy.newProxyInstance(
			raw.getClass().getClassLoader(),
			raw.getClass().getInterfaces(),
			new MemcacheServiceRetryProxy(raw, retryCount));
	}
	
	/** */
	private final MemcacheService raw;
	
	/** */
	private final int tries;
	
	/** */
	public MemcacheServiceRetryProxy(MemcacheService raw, int tries) {
		this.raw = raw;
		this.tries = tries;
	}

	/* (non-Javadoc)
	 * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	public Object invoke(Object proxy, Method meth, Object[] args) throws Throwable {
		for (int i = 0; i<this.tries; i++) {
			try {
				return meth.invoke(this.raw, args);
			} catch (InvocationTargetException ex) {
				if (i == (this.tries - 1))
					log.error("Memcache operation failed, giving up", ex);
				else
					log.warn("Error performing memcache operation, retrying: " + meth, ex);
			}
		}
		
		// Will reach this point of we have exhausted our retries.
		return null;
	}

}