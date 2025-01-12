package com.googlecode.objectify.test.util;

import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.util.Closeable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Sets up and tears down the GAE local unit test harness environment
 */
public class ObjectifyExtension implements BeforeEachCallback, AfterEachCallback {

	private static final Namespace NAMESPACE = Namespace.create(ObjectifyExtension.class);

	@Override
	public void beforeEach(final ExtensionContext context) throws Exception {
		ObjectifyService.setFactory(new ObjectifyFactory());

		final Closeable rootService = ObjectifyService.begin();

		context.getStore(NAMESPACE).put(Closeable.class, rootService);
	}

	@Override
	public void afterEach(final ExtensionContext context) throws Exception {
		final Closeable rootService = context.getStore(NAMESPACE).get(Closeable.class, Closeable.class);

		rootService.close();
	}
}
