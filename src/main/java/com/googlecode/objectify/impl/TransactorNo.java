package com.googlecode.objectify.impl;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.TxnType;
import com.googlecode.objectify.Work;
import lombok.extern.slf4j.Slf4j;

import java.util.ConcurrentModificationException;

/**
 * Transactor which represents the absence of a transaction.
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
@Slf4j
class TransactorNo extends Transactor
{
	/**
	 */
	TransactorNo(final Objectify ofy) {
		super(ofy);
	}

	/**
	 */
	TransactorNo(final Objectify ofy, final Session session) {
		super(ofy, session);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#getTxn()
	 */
	@Override
	public TransactionImpl getTransaction() {
		// This version doesn't have a transaction, always null.
		return null;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.impl.cmd.Transactor#transactionless()
	 */
	@Override
	public ObjectifyImpl transactionless(ObjectifyImpl parent) {
		return parent;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.impl.cmd.Transactor#execute(com.googlecode.objectify.TxnType, com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R execute(final ObjectifyImpl parent, final TxnType txnType, final Work<R> work) {
		switch (txnType) {
			case MANDATORY:
				throw new IllegalStateException("MANDATORY transaction but no transaction present");

			case NOT_SUPPORTED:
			case NEVER:
			case SUPPORTS:
				return work.run();

			case REQUIRED:
			case REQUIRES_NEW:
				return transact(parent, work);

			default:
				throw new IllegalStateException("Impossible, some unknown txn type");
		}

	}

	@Override
	public <R> R transactionless(final ObjectifyImpl parent, final Work<R> work) {
		return work.run();
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.impl.Transactor#transact(com.googlecode.objectify.impl.ObjectifyImpl, com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R transact(final ObjectifyImpl parent, final Work<R> work) {
		return this.transactNew(parent, Integer.MAX_VALUE, work);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.impl.Transactor#transactNew(com.googlecode.objectify.impl.ObjectifyImpl, int, com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R transactNew(final ObjectifyImpl parent, int limitTries, final Work<R> work) {
		Preconditions.checkArgument(limitTries >= 1);

		while (true) {
			try {
				return transactOnce(parent, work);
			} catch (ConcurrentModificationException ex) {
				if (--limitTries > 0) {
					log.warn("Optimistic concurrency failure for {} (retrying): {}", work, ex);
					log.trace("Details of optimistic concurrency failure", ex);
				} else {
					throw ex;
				}
			}
		}
	}

	/**
	 * One attempt at executing a transaction
	 */
	private <R> R transactOnce(final ObjectifyImpl parent, final Work<R> work) {
		final ObjectifyImpl txnOfy = factory.open(parent.getOptions(), next -> new TransactorYes(next, this));

		boolean committedSuccessfully = false;
		try {
			final R result = work.run();
			txnOfy.flush();
			txnOfy.getTransaction().commit();
			committedSuccessfully = true;
			return result;
		}
		finally {
			if (txnOfy.getTransaction().isActive()) {
				try {
					txnOfy.getTransaction().rollback();
				} catch (RuntimeException ex) {
					log.error("Rollback failed, suppressing error", ex);
				}
			}

			txnOfy.close();

			if (committedSuccessfully) {
				txnOfy.getTransaction().runCommitListeners();
			}
		}
	}
}