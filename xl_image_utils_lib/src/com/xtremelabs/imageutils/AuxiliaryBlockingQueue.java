package com.xtremelabs.imageutils;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.os.AsyncTask;

public class AuxiliaryBlockingQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {

	private final AuxiliaryQueue mQueue;
	private final ReentrantLock mLock = new ReentrantLock(true);
	private final Condition mNotEmpty;
	private int mCount = 0;

	public AuxiliaryBlockingQueue(PriorityAccessor[] accessors) {
		mQueue = new AuxiliaryQueue(accessors);
		mNotEmpty = mLock.newCondition();
	}

	public void bump(final Runnable e) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				mLock.lock();
				try {
					mQueue.bump((Prioritizable) e);
					return null;
				} finally {
					mLock.unlock();
				}
			}
		}.execute();
	}

	@Override
	public boolean offer(Runnable e) {
		checkNotNull(e);
		mLock.lock();
		try {
			insert(e);
			return true;
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public synchronized Runnable peek() {
		mLock.lock();
		try {
			return mQueue.peek();
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public Runnable poll() {
		mLock.lock();
		try {
			return extract();
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public Iterator<Runnable> iterator() {
		// TODO Fill this in.
		return null;
	}

	@Override
	public int size() {
		mLock.lock();
		try {
			return mQueue.size();
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public int drainTo(Collection<? super Runnable> collection) {
		checkNotNull(collection);
		mLock.lock();
		try {
			int numDrained = 0;
			while (mQueue.size() > 0) {
				collection.add(extract());
				numDrained++;
			}
			// FIXME We need to signal an empty queue here.
			return numDrained;
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public int drainTo(Collection<? super Runnable> collection, int maxNumberToDrain) {
		checkNotNull(collection);
		mLock.lock();
		try {
			int numDrained = 0;
			for (int i = 0; i < maxNumberToDrain; i++) {
				Runnable runnable = extract();
				if (runnable == null) {
					break;
				} else {
					collection.add(runnable);
					numDrained++;
				}
			}
			return numDrained;
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public boolean offer(Runnable e, long timeout, TimeUnit unit) throws InterruptedException {
		checkNotNull(e);
		mLock.lockInterruptibly();
		try {
			insert(e);
			return true;
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
		long nanos = unit.toNanos(timeout);
		mLock.lockInterruptibly();
		try {
			while (mQueue.size() == 0) {
				if (nanos <= 0)
					return null;
				nanos = mNotEmpty.awaitNanos(nanos);
			}
			return extract();
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public void put(Runnable e) throws InterruptedException {
		checkNotNull(e);
		mLock.lockInterruptibly();
		try {
			insert(e);
		} finally {
			mLock.unlock();
		}
	}

	@Override
	public int remainingCapacity() {
		return Integer.MAX_VALUE;
	}

	@Override
	public Runnable take() throws InterruptedException {
		mLock.lockInterruptibly();
		try {
			while (mQueue.isEmpty())
				mNotEmpty.await();
			return extract();
		} finally {
			mLock.unlock();
		}
	}

	private void insert(Runnable r) {
		mQueue.add((Prioritizable) r);
		mCount++;
		if (mCount == 1)
			mNotEmpty.signal();
	}

	private Runnable extract() {
		Runnable runnable = mQueue.removeHighestPriorityRunnable();
		if (runnable != null)
			mCount--;
		return runnable;
	}

	private void checkNotNull(Object o) {
		if (o == null)
			throw new NullPointerException();
	}
}
