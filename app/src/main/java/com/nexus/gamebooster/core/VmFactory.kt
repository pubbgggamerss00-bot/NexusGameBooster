package com.nexus.gamebooster.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Minimal ViewModel factory: creates the ViewModel from a plain lambda.
 * Usage inside a screen composable:
 *   val vm: HomeViewModel = viewModel(factory = VmFactory { ServiceLocator.homeViewModel() })
 */
class VmFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
