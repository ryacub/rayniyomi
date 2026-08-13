package tachiyomi.core.common.util.lang

import rx.Observable
import rx.plugins.RxJavaHooks

/**
 * Contains a [LinkageError] that a defective extension raises inside an RxJava observable.
 *
 * RxJava 1 treats [LinkageError] as fatal: `Exceptions.throwIfFatal` rethrows it instead of giving
 * it to `onError`. On the subscriber thread it then leaves `Observable.subscribe` and stops the
 * process. On a scheduler thread, `ScheduledAction` gives it to the thread uncaught handler, where
 * no call-site `catch` can reach it and the waiting continuation never resumes.
 *
 * Both `Observable.subscribe` and `Observable.unsafeSubscribe` pass their `OnSubscribe` through
 * `RxJavaHooks.onObservableStart`, and `OperatorSubscribeOn` calls `unsafeSubscribe` inside its
 * worker action. A hook therefore runs on the same thread as the extension code and can report the
 * fault through `onError` before RxJava treats it as fatal.
 *
 * Only [LinkageError] is contained. `VirtualMachineError` and other errors keep the normal path.
 */
object SourceLinkageGuard {

    /**
     * Installs the hook. Call once, as early as possible in app start.
     *
     * The hook is a global static in RxJava, so a later `RxJavaHooks.reset()` removes it.
     */
    fun install() {
        RxJavaHooks.setOnObservableStart { _, onSubscribe -> contain(onSubscribe) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun contain(onSubscribe: Observable.OnSubscribe<*>): Observable.OnSubscribe<*> {
        val delegate = onSubscribe as Observable.OnSubscribe<Any>
        return Observable.OnSubscribe<Any> { subscriber ->
            try {
                delegate.call(subscriber)
            } catch (error: LinkageError) {
                // Report every containment. The hook covers all observables, not only extension
                // ones, so an app-side minification fault must not disappear here.
                subscriber.onError(error.reportAsSourceFailure())
            }
        }
    }
}
