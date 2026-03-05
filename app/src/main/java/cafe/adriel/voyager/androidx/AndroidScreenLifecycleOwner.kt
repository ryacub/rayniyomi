// Shadowing cafe.adriel.voyager:voyager-core 1.0.1 to fix:
//   IllegalStateException: State is 'DESTROYED' and cannot be moved to `STARTED`
//   DESTROYED is terminal — any handleLifecycleEvent() call after it is illegal.
//   All event dispatching goes through safeHandleLifecycleEvent() which no-ops
//   when already DESTROYED.
// Remove this file once Voyager upstream ships the fix.
package cafe.adriel.voyager.androidx

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import cafe.adriel.voyager.core.lifecycle.ScreenLifecycleOwner
import cafe.adriel.voyager.core.lifecycle.ScreenLifecycleStore
import cafe.adriel.voyager.core.screen.Screen
import java.util.concurrent.atomic.AtomicReference

public class AndroidScreenLifecycleOwner private constructor() :
    ScreenLifecycleOwner,
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {

    override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val atomicContext = AtomicReference<Context>()
    internal val atomicParentLifecycleOwner = AtomicReference<LifecycleOwner>()

    private val controller = SavedStateRegistryController.create(this)

    private var isCreated: Boolean by mutableStateOf(false)

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = SavedStateViewModelFactory(
            application = atomicContext.get()?.applicationContext?.getApplication(),
            owner = this,
        )

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            val application = atomicContext.get()?.applicationContext?.getApplication()
            if (application != null) {
                set(AndroidViewModelFactory.APPLICATION_KEY, application)
            }
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@AndroidScreenLifecycleOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@AndroidScreenLifecycleOwner)
        }

    init {
        controller.performAttach()
        enableSavedStateHandles()
    }

    /** No-ops when lifecycle is already DESTROYED (terminal state). */
    private fun safeHandleLifecycleEvent(event: Lifecycle.Event) {
        if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
            lifecycle.handleLifecycleEvent(event)
        }
    }

    private fun onCreate(savedState: Bundle?) {
        check(!isCreated) { "onCreate already called" }
        isCreated = true
        controller.performRestore(savedState)
        initEvents.forEach(::safeHandleLifecycleEvent)
    }

    private fun emitOnStartEvents() {
        startEvents.forEach(::safeHandleLifecycleEvent)
    }

    private fun emitOnStopEvents() {
        stopEvents.forEach(::safeHandleLifecycleEvent)
    }

    @Composable
    override fun ProvideBeforeScreenContent(
        provideSaveableState: @Composable (suffixKey: String, content: @Composable () -> Unit) -> Unit,
        content: @Composable () -> Unit,
    ) {
        provideSaveableState("lifecycle") {
            LifecycleDisposableEffect()

            val hooks = getHooks()

            CompositionLocalProvider(*hooks.toTypedArray()) {
                content()
            }
        }
    }

    override fun onDispose(screen: Screen) {
        val context = atomicContext.getAndSet(null) ?: return
        val activity = context.getActivity()
        if (activity != null && activity.isChangingConfigurations) return
        viewModelStore.clear()
        disposeEvents.forEach(::safeHandleLifecycleEvent)
    }

    private fun performSave(outState: Bundle) {
        controller.performSave(outState)
    }

    @Composable
    private fun getHooks(): List<ProvidedValue<*>> {
        atomicContext.compareAndSet(null, LocalContext.current)
        atomicParentLifecycleOwner.compareAndSet(null, LocalLifecycleOwner.current)

        return remember(this) {
            listOf(
                LocalLifecycleOwner provides this,
                LocalViewModelStoreOwner provides this,
                LocalSavedStateRegistryOwner provides this,
            )
        }
    }

    private fun registerLifecycleListener(outState: Bundle): () -> Unit {
        val lifecycleOwner = atomicParentLifecycleOwner.get()
        if (lifecycleOwner != null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onPause(owner: LifecycleOwner) =
                    safeHandleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

                override fun onResume(owner: LifecycleOwner) =
                    safeHandleLifecycleEvent(Lifecycle.Event.ON_RESUME)

                override fun onStart(owner: LifecycleOwner) =
                    safeHandleLifecycleEvent(Lifecycle.Event.ON_START)

                override fun onStop(owner: LifecycleOwner) {
                    safeHandleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    performSave(outState)
                }
            }
            val lifecycle = lifecycleOwner.lifecycle
            lifecycle.addObserver(observer)

            return { lifecycle.removeObserver(observer) }
        } else {
            return {}
        }
    }

    @Composable
    private fun LifecycleDisposableEffect() {
        val savedState = rememberSaveable { Bundle() }
        if (!isCreated) {
            onCreate(savedState)
        }

        DisposableEffect(this) {
            val unregisterLifecycle = registerLifecycleListener(savedState)
            emitOnStartEvents()

            onDispose {
                unregisterLifecycle()
                performSave(savedState)
                emitOnStopEvents()
            }
        }
    }

    private tailrec fun Context.getActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> null
    }

    private tailrec fun Context.getApplication(): Application? = when (this) {
        is Application -> this
        is ContextWrapper -> baseContext.getApplication()
        else -> null
    }

    public companion object {

        private val initEvents = arrayOf(
            Lifecycle.Event.ON_CREATE,
        )

        private val startEvents = arrayOf(
            Lifecycle.Event.ON_START,
            Lifecycle.Event.ON_RESUME,
        )

        private val stopEvents = arrayOf(
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_STOP,
        )

        private val disposeEvents = arrayOf(
            Lifecycle.Event.ON_DESTROY,
        )

        public fun get(screen: Screen): ScreenLifecycleOwner {
            return ScreenLifecycleStore.get(screen) { AndroidScreenLifecycleOwner() }
        }
    }
}
