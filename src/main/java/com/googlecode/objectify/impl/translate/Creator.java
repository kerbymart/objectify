package com.googlecode.objectify.impl.translate;

import com.google.appengine.api.datastore.PropertyContainer;
import com.googlecode.objectify.impl.Forge;
import com.googlecode.objectify.impl.Path;
import com.googlecode.objectify.util.LogUtils;
import lombok.extern.slf4j.Slf4j;


/**
 * <p>Factory for POJO and PropertyContainer objects. Lets us hide the distinction between
 * Entity creation and embedded object creation.</p>
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
@Slf4j
abstract public class Creator<P> implements Translator<P, PropertyContainer>
{
	private final Class<P> clazz;
	private final Forge forge;

	/**
	 */
	Creator(final Class<P> clazz, final Forge forge) {
		this.clazz = clazz;
		this.forge = forge;
	}

	/**
	 * Make an instance of the thing
	 */
	protected P construct(final Path path) {
		if (log.isTraceEnabled())
			log.trace(LogUtils.msg(path, "Instantiating a " + clazz.getName()));

		return forge.construct(clazz);
	}
}
