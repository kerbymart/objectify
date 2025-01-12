package com.googlecode.objectify.test;

import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.common.collect.Maps;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.test.util.TestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.googlecode.objectify.ObjectifyService.factory;
import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 */
class EmbeddedMapFormatTests extends TestBase {
	
	/** */
	@com.googlecode.objectify.annotation.Entity
	@Cache
	private static class OuterWithMap {
		@Id Long id;
		Map<String, Long> map = Maps.newLinkedHashMap();
	}
	
	/** */
	@Test
	void v2EmbedMapFormatIsCorrect() throws Exception {
		factory().register(OuterWithMap.class);

		final OuterWithMap outer = new OuterWithMap();
		outer.map.put("asdf", 123L);
		
		final Key<OuterWithMap> key = ofy().save().entity(outer).now();
		
		final Entity entity = ds().get(key.getRaw());
		
		final EmbeddedEntity entityInner = (EmbeddedEntity)entity.getProperty("map");
		assertThat(entityInner.getProperty("asdf")).isEqualTo(123L);
		
		ofy().clear();
		final OuterWithMap fetched = ofy().load().key(key).now();
		assertThat(fetched.map).isEqualTo(outer.map);
	}
}
