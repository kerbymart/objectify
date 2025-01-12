package com.googlecode.objectify.test;

import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.common.collect.Lists;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.test.util.TestBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.googlecode.objectify.ObjectifyService.factory;
import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 */
class EmbeddingFormatTests extends TestBase {

	/** */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class Inner {
		private String stuff;
	}

	/** */
	@com.googlecode.objectify.annotation.Entity
	@Cache
	@Data
	@NoArgsConstructor
	private static class Outer {
		@Id
		private Long id;

		private Inner inner;

		public Outer(Inner inner) {
			this.inner = inner;
		}
	}
	
	/** */
	@Test
	void simpleOneLayerEmbedding() throws Exception {
		factory().register(Outer.class);

		final Inner inner = new Inner("stuff");
		final Outer outer = new Outer(inner);

		final Key<Outer> key = ofy().save().entity(outer).now();

		final Entity entity = ds().get(key.getRaw());

		final EmbeddedEntity entityInner = (EmbeddedEntity)entity.getProperty("inner");
		assertThat(entityInner.getProperty("stuff")).isEqualTo("stuff");
		
		ofy().clear();
		final Outer fetched = ofy().load().key(key).now();
		assertThat(fetched.inner.stuff).isEqualTo(inner.stuff);
	}
	
	/** */
	@com.googlecode.objectify.annotation.Entity
	@Cache
	private static class OuterWithList {
		@Id private Long id;
		private List<Inner> inner = Lists.newArrayList();
		public OuterWithList() { }
	}
	
	/** */
	@Test
	void embeddedList() throws Exception {
		factory().register(OuterWithList.class);

		final OuterWithList outer = new OuterWithList();
		outer.inner.add(new Inner("stuff0"));
		outer.inner.add(new Inner("stuff1"));

		final Key<OuterWithList> key = ofy().save().entity(outer).now();

		final Entity entity = ds().get(key.getRaw());
		
		@SuppressWarnings("unchecked")
		final List<EmbeddedEntity> entityInner = (List<EmbeddedEntity>)entity.getProperty("inner");
		assertThat(entityInner.size()).isEqualTo(2);
		assertThat(entityInner.get(0).getProperty("stuff")).isEqualTo("stuff0");
		assertThat(entityInner.get(1).getProperty("stuff")).isEqualTo("stuff1");

		ofy().clear();
		final OuterWithList fetched = ofy().load().key(key).now();
		assertThat(fetched.inner.get(0).stuff).isEqualTo(outer.inner.get(0).stuff);
		assertThat(fetched.inner.get(1).stuff).isEqualTo(outer.inner.get(1).stuff);
	}
	
	/** */
	@com.googlecode.objectify.annotation.Entity
	@Cache
	@Data
	private static class HasEmbeddedEntity {
		@Id
		private Long id;
		private EmbeddedEntity normal;
	}
	
	/** */
	@Test
	void normalEmbeddedEntityFieldWorksFine() throws Exception {
		factory().register(HasEmbeddedEntity.class);

		final HasEmbeddedEntity h = new HasEmbeddedEntity();
		h.normal = new EmbeddedEntity();
		h.normal.setProperty("stuff", "stuff");
		
		final HasEmbeddedEntity fetched = saveClearLoad(h);
		assertThat(fetched.normal).isEqualTo(h.normal);
	}
	
	/** */
	@com.googlecode.objectify.annotation.Entity
	@Cache
	@Data
	private static class HasEmbeddedEntityList {
		@Id
		private Long id;
		private List<EmbeddedEntity> list = Lists.newArrayList();
	}
	
	/** */
	@Test
	void listEmbeddedEntityFieldWorksFine() throws Exception {
		factory().register(HasEmbeddedEntityList.class);

		final HasEmbeddedEntityList h = new HasEmbeddedEntityList();

		final EmbeddedEntity emb0 = new EmbeddedEntity();
		emb0.setProperty("stuff", "stuff0");
		h.list.add(emb0);
		
		final HasEmbeddedEntityList fetched = saveClearLoad(h);
		assertThat(fetched.list).isEqualTo(h.list);
	}

	/** */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class InnerIndexed {
		@Index private String stuff;
	}

	/** */
	@com.googlecode.objectify.annotation.Entity
	@Cache
	@Data
	@NoArgsConstructor
	private static class OuterWithIndex {
		@Id private Long id;

		private InnerIndexed inner;

		public OuterWithIndex(InnerIndexed inner) {
			this.inner = inner;
		}
	}
	
	/** */
	@Test
	void indexFormatIsCorrect() throws Exception {
		factory().register(OuterWithIndex.class);

		final InnerIndexed inner = new InnerIndexed("stuff");
		final OuterWithIndex outer = new OuterWithIndex(inner);

		final Key<OuterWithIndex> key = ofy().save().entity(outer).now();

		final Entity entity = ds().get(key.getRaw());
		assertThat(entity.getProperties()).hasSize(2);
		assertThat(entity.getProperty("inner.stuff")).isEqualTo(Collections.singletonList("stuff"));
		assertThat(entity.isUnindexedProperty("inner.stuff")).isFalse();
		
		ofy().clear();
		final OuterWithIndex fetched = ofy().load().type(OuterWithIndex.class).filter("inner.stuff", "stuff").iterator().next();
		assertThat(fetched.inner).isEqualTo(inner);
	}

	/** */
	@Test
	void batchSaveDoesNotCrossContaminateIndexes() throws Exception {
		factory().register(OuterWithIndex.class);

		final InnerIndexed inner0 = new InnerIndexed("stuff0");
		final OuterWithIndex outer0 = new OuterWithIndex(inner0);

		final InnerIndexed inner1 = new InnerIndexed("stuff1");
		final OuterWithIndex outer1 = new OuterWithIndex(inner1);

		ofy().save().entities(outer0, outer1).now();

		ofy().clear();
		final List<OuterWithIndex> fetched = ofy().load().type(OuterWithIndex.class).filter("inner.stuff", inner0.stuff).list();

		assertThat(fetched).containsExactly(outer0);
	}
}
