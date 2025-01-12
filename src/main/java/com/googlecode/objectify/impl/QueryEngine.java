package com.googlecode.objectify.impl;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.datastore.Transaction;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.util.DatastoreUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Logic for dealing with queries.
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
@Slf4j
public class QueryEngine
{
	/** */
	protected final LoaderImpl loader;
	protected final AsyncDatastoreService ads;
	protected final Transaction transactionRaw;

	/**
	 */
	public QueryEngine(LoaderImpl loader, AsyncDatastoreService ads, Transaction transactionRaw) {
		this.loader = loader;
		this.ads = ads;
		this.transactionRaw = transactionRaw;
	}

	/**
	 * Perform a keys-only query.
	 */
	public <T> QueryResultIterable<Key<T>> queryKeysOnly(com.google.appengine.api.datastore.Query query, final FetchOptions fetchOpts) {
		assert query.isKeysOnly();
		log.trace("Starting keys-only query");

		final PreparedQuery pq = prepare(query);

		return () -> new KeysOnlyIterator<>(pq, fetchOpts);
	}

	/**
	 * Perform a keys-only plus batch gets.
	 */
	public <T> QueryResultIterable<T> queryHybrid(com.google.appengine.api.datastore.Query query, final FetchOptions fetchOpts) {
		assert !query.isKeysOnly();
		log.trace("Starting hybrid query");

		query = DatastoreUtils.cloneQuery(query).setKeysOnly();

		final PreparedQuery pq = prepare(query);

		return () -> new ChunkingIterator<>(loader.createLoadEngine(), pq, new KeysOnlyIterator<T>(pq, fetchOpts), fetchOpts.getChunkSize());
	}

	/**
	 * A normal, non-hybrid query
	 */
	public <T> QueryResultIterable<T> queryNormal(com.google.appengine.api.datastore.Query query, final FetchOptions fetchOpts) {
		assert !query.isKeysOnly();
		log.trace("Starting normal query");

		final PreparedQuery pq = prepare(query);
		final LoadEngine loadEngine = loader.createLoadEngine();

		return new QueryResultIterable<T>() {
			@Override
			public QueryResultIterator<T> iterator() {
				return new ChunkingIterator<>(loadEngine, pq, new StuffingIterator<T>(pq, fetchOpts, loadEngine), fetchOpts.getChunkSize());
			}
		};
	}

	/**
	 * A projection query. Bypasses the session entirely.
	 */
	public <T> QueryResultIterable<T> queryProjection(com.google.appengine.api.datastore.Query query, final FetchOptions fetchOpts) {
		assert !query.isKeysOnly();
		assert !query.getProjections().isEmpty();
		log.trace("Starting projection query");

		final PreparedQuery pq = prepare(query);
		final LoadEngine loadEngine = loader.createLoadEngine();

		return () -> new ProjectionIterator<>(pq.asQueryResultIterator(fetchOpts), loadEngine);
	}

	/**
	 * The fundamental query count operation.  This is sufficiently different from normal query().
	 */
	public int queryCount(com.google.appengine.api.datastore.Query query, FetchOptions fetchOpts) {
		PreparedQuery pq = prepare(query);
		return pq.countEntities(fetchOpts);
	}

	/** */
	private PreparedQuery prepare(com.google.appengine.api.datastore.Query query) {
		return ads.prepare(transactionRaw, query);
	}
}