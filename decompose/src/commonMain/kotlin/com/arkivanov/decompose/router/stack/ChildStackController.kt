package com.arkivanov.decompose.router.stack

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.SerializedQueue
import com.arkivanov.decompose.ensureNeverFrozen
import com.arkivanov.decompose.router.stack.StackNavigationSource.Event
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backpressed.BackPressedHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy

internal class ChildStackController<out C : Any, out T : Any>(
    private val source: StackNavigationSource<C>,
    lifecycle: Lifecycle,
    private val backPressedHandler: BackPressedHandler,
    private val popOnBackPressed: Boolean,
    private val stackHolder: StackHolder<C, T>,
    private val controller: StackController<C, T>,
) {

    init {
        ensureNeverFrozen()
    }

    private val queue = SerializedQueue(::navigateActual)
    private val _stack = MutableValue(stackHolder.stack.toState())
    val stack: Value<ChildStack<C, T>> get() = _stack

    init {
        val eventObserver = queue::offer
        source.subscribe(eventObserver)

        val onBackPressedHandler = ::onBackPressed
        backPressedHandler.register(onBackPressedHandler)

        lifecycle.doOnDestroy {
            backPressedHandler.unregister(onBackPressedHandler)
            source.unsubscribe(eventObserver)
        }
    }

    private fun navigateActual(event: Event<C>) {
        val oldStack = stackHolder.stack
        val newStack = controller.navigate(oldStack = oldStack, transformer = event.transformer)
        stackHolder.stack = newStack
        _stack.value = newStack.toState()
        event.onComplete(newStack.configurationStack, oldStack.configurationStack)
    }

    private fun onBackPressed(): Boolean =
        when {
            stackHolder.stack.active.backPressedDispatcher.onBackPressed() -> true

            popOnBackPressed && stackHolder.stack.backStack.isNotEmpty() -> {
                queue.offer(Event(transformer = { it.dropLast(1) }))
                true
            }

            else -> false
        }

    private fun RouterStack<C, T>.toState(): ChildStack<C, T> =
        ChildStack(
            active = Child.Created(configuration = active.configuration, instance = active.instance),
            backStack = backStack.map { it.toChild() }
        )

    private fun RouterEntry<C, T>.toChild(): Child<C, T> =
        when (this) {
            is RouterEntry.Created -> Child.Created(configuration = configuration, instance = instance)
            is RouterEntry.Destroyed -> Child.Destroyed(configuration = configuration)
        }
}