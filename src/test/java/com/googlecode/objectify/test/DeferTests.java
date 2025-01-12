/*
 */

package com.googlecode.objectify.test;

import com.google.appengine.api.datastore.EntityNotFoundException;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.OnSave;
import com.googlecode.objectify.test.entity.Trivial;
import com.googlecode.objectify.test.util.TestBase;
import com.googlecode.objectify.util.Closeable;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.googlecode.objectify.ObjectifyService.factory;
import static com.googlecode.objectify.ObjectifyService.ofy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of defer()
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
class DeferTests extends TestBase {

	@BeforeEach
	void setUp() throws Exception {
		factory().register(Trivial.class);
	}

	/** */
	@Test
	void deferredSaveWithAutogeneratedId() throws Exception {
		final Trivial triv = new Trivial("foo", 5);

		try (final Closeable root = ObjectifyService.begin()) {
			ofy().defer().save().entity(triv);
		}

		assertThat(triv.getId()).isNotNull();
	}

	/** */
	@Test
	void deferredSaveAndDeleteProcessedAtEndOfRequest() throws Exception {

		final Trivial triv = new Trivial(123L, "foo", 5);

		try (final Closeable root = ObjectifyService.begin()) {
			ofy().defer().save().entity(triv);

			// Can load out of session
			assertThat(ofy().load().entity(triv).now()).isEqualTo(triv);

			// But not the datastore
			assertThrows(
					EntityNotFoundException.class,
					() -> ds().get(null, Key.create(triv).getRaw()),
					"Entity should not have been saved yet");
		}

		try (final Closeable root = ObjectifyService.begin()) {
			final Trivial loaded = ofy().load().entity(triv).now();
			assertThat(loaded).isEqualTo(triv);
		}

		try (final Closeable root = ObjectifyService.begin()) {
			ofy().defer().delete().entity(triv);

			// Deleted in session
			assertThat(ofy().load().entity(triv).now()).isNull();

			// But not datastore
			try {
				ds().get(null, Key.create(triv).getRaw());
			} catch (EntityNotFoundException e) {
				assert false : "Entity should not have been deleted yet";
			}
		}

		try (final Closeable root = ObjectifyService.begin()) {
			final Trivial loaded = ofy().load().entity(triv).now();
			assertThat(loaded).isNull();
		}
	}

	/** */
	@Test
	void deferredSaveAndDeleteProcessedAtEndOfTransaction() throws Exception {

		final Trivial triv = new Trivial(123L, "foo", 5);

		try (final Closeable root = ObjectifyService.begin()) {

			ofy().transact(() -> {
				ofy().defer().save().entity(triv);

				// Can load out of session
				assertThat(ofy().load().entity(triv).now()).isEqualTo(triv);

				// But not datastore
				assertThrows(
						EntityNotFoundException.class,
						() -> ds().get(null, Key.create(triv).getRaw()),
						"Entity should not have been saved yet");
			});

			{
				final Trivial loaded = ofy().load().entity(triv).now();
				assertThat(loaded).isEqualTo(triv);
			}

			ofy().transact(() -> {
				ofy().defer().delete().entity(triv);

				// Deleted in session
				assertThat(ofy().load().entity(triv).now()).isNull();

				// But not datastore
				try {
					ds().get(null, Key.create(triv).getRaw());
				} catch (EntityNotFoundException e) {
					assert false : "Entity should not have been deleted yet";
				}
			});

			{
				final Trivial loaded = ofy().load().entity(triv).now();
				assertThat(loaded).isNull();
			}
		}
	}

	@Entity
	@Data
	private static class HasOnSave {
		@Id
		private Long id;
		private String data;

		@OnSave
		void changeData() {
			data = "onsaved";
		}
	}

	/** */
	@Test
	void deferredSaveTriggersOnSaveMethods() throws Exception {
		factory().register(HasOnSave.class);
		final HasOnSave hos = new HasOnSave();

		try (final Closeable root = ObjectifyService.begin()) {
			ofy().defer().save().entity(hos);
		}

		assertThat(hos.getData()).isEqualTo("onsaved");
	}
}