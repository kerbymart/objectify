/*
 */

package com.googlecode.objectify.test;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.test.entity.Trivial;
import com.googlecode.objectify.test.util.TestBase;
import com.googlecode.objectify.util.Closeable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.googlecode.objectify.ObjectifyService.factory;
import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * Tests of various queries
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
class QueryPerformanceTests extends TestBase {

	/** */
	class CountingProxy implements InvocationHandler {

		AsyncDatastoreService base;

		CountingProxy(AsyncDatastoreService base) {
			this.base = base;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getName().equals("get"))
				getCount++;

			return method.invoke(base, args);
		}
	}

	/** */
	private Trivial triv1;
	private int getCount;

	/** TODO: kill when stack in factory */
	private Closeable rootService;

	/** */
	@BeforeEach
	void setUpExtra() {
		getCount = 0;

		// throw away the current factory and replace it with one that tracks calls
		ObjectifyService.setFactory(new ObjectifyFactory() {
			@Override
			protected AsyncDatastoreService createRawAsyncDatastoreService(DatastoreServiceConfig cfg) {
				return (AsyncDatastoreService)Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{AsyncDatastoreService.class}, new CountingProxy(super.createRawAsyncDatastoreService(cfg)));
			}
		});

		factory().register(Trivial.class);

		// Need to push down the ofy from the extension; this goes away when the stack is owned by the factory
		// TODO: kill when stack in factory
		rootService = ObjectifyService.begin();

		this.triv1 = new Trivial("foo1", 1);

		ofy().save().entity(triv1).now();
		ofy().clear();
	}

	/** TODO: kill when stack in factory */
	@AfterEach
	void tearDownExtra() {
		rootService.close();
	}

	/** */
	@Test
	void hybridOn() throws Exception {
		final List<Trivial> list = ofy().load().type(Trivial.class).hybrid(true).list();
		assertThat(list).containsExactly(triv1);
		assertThat(getCount).isEqualTo(1);
	}

	/** */
	@Test
	void hybridOff() throws Exception {
		final List<Trivial> list = ofy().load().type(Trivial.class).hybrid(false).list();
		assertThat(list).containsExactly(triv1);
		assertThat(getCount).isEqualTo(0);
	}

	/**
	 * At one point you couldn't have an IN query with keysonly and sort.
	 */
	@Test
	void hybridQueryWithSortAndIN() throws Exception {
		final List<Trivial> list = ofy().load()
				.type(Trivial.class)
				.filter("someString in", Arrays.asList("foo1", "foo2"))
				.order("someString").list();

		assertThat(list).containsExactly(triv1);
		assertThat(getCount).isEqualTo(1);
	}

	/**
	 * At one point you couldn't have a NOT query with keysonly and sort.
	 */
	@Test
	void hybridQueryWithSortAndNOT() throws Exception {
		final List<Trivial> list = ofy().load()
				.type(Trivial.class)
				.filter("someString !=", "foo2")
				.order("someString").list();

		assertThat(list).containsExactly(triv1);
		assertThat(getCount).isEqualTo(1);
	}
}
