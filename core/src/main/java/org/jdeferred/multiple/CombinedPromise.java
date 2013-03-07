/*
 * Copyright 2013 Ray Tsang
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdeferred.multiple;

import java.util.concurrent.atomic.AtomicInteger;

import org.jdeferred.AlwaysCallback;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

/**
 * This will return a special Promise called {@link CombinedPromise}. In short,
 * <ul>
 * <li>{@link Promise#done(DoneCallback)} will be triggered if all promises resolves
 * (i.e., all finished successfully) with {@link MultipleResults}.</li>
 * <li>{@link Promise#fail(FailCallback)} will be
 * triggered if any promises rejects (i.e., if any one failed) with {@link OneReject}.</li>
 * <li>{@link Promise#progress(ProgressCallback)} will be triggered whenever one
 * promise resolves or rejects ({#link {@link CombinedPromiseProgress}), 
 * or whenever a promise was notified progress ({@link OneProgress}).</li>
 * <li>{@link Promise#always(AlwaysCallback)} will be triggered whenever
 * {@link Promise#done(DoneCallback)} or {@link Promise#fail(FailCallback)}
 * would be triggered</li>
 * </ul>
 * 
 * @author Ray Tsang
 * 
 */
@SuppressWarnings("rawtypes")
public class CombinedPromise extends
		DeferredObject<MultipleResults, OneReject, CombinedPromiseProgress>
		implements Promise<MultipleResults, OneReject, CombinedPromiseProgress> {
	private final int numberOfPromises;
	private final AtomicInteger doneCount = new AtomicInteger();
	private final AtomicInteger failCount = new AtomicInteger();
	private final MultipleResults results;

	@SuppressWarnings("unchecked")
	public CombinedPromise(Promise... promises) {
		if (promises == null || promises.length == 0)
			throw new IllegalArgumentException("Promises is null or empty");
		this.numberOfPromises = promises.length;
		results = new MultipleResults(numberOfPromises);

		int count = 0;
		for (final Promise promise : promises) {
			final int index = count++;
			promise.fail(new FailCallback<Object>() {
				public void onFail(Object result) {
					if (!CombinedPromise.this.isPending())
						return;

					
					final int fail = failCount.incrementAndGet();
					CombinedPromise.this.notify(new CombinedPromiseProgress(
							doneCount.get(),
							fail,
							numberOfPromises));
					
					CombinedPromise.this.reject(new OneReject(index, promise, result));
				}
			}).progress(new ProgressCallback() {
				public void onProgress(Object progress) {
					if (!CombinedPromise.this.isPending())
						return;

					CombinedPromise.this.notify(new OneProgress(
							doneCount.get(),
							failCount.get(),
							numberOfPromises, index, promise, progress));
				}
			}).done(new DoneCallback() {
				public void onDone(Object result) {
					if (!CombinedPromise.this.isPending())
						return;

					results.set(index, new OneResult(index, promise,
							result));
					int done = doneCount.incrementAndGet();

					CombinedPromise.this.notify(new CombinedPromiseProgress(
							done,
							failCount.get(),
							numberOfPromises));
					
					if (done == numberOfPromises)
						CombinedPromise.this.resolve(results);
				}
			});
		}
	}
}