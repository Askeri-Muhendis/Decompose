package com.arkivanov.decompose

import com.arkivanov.decompose.statekeeper.Parcelable
import kotlin.reflect.KClass

interface RouterFactory {

    fun <C : Parcelable, T : Any> router(
        initialConfiguration: () -> C,
        initialBackStack: () -> List<C> = ::emptyList,
        configurationClass: KClass<out C>,
        key: String = "DefaultRouter",
        handleBackButton: Boolean = false,
        childFactory: (configuration: C, ComponentContext) -> T
    ): Router<C, T>
}
