/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What Settings knows about the glasses: where registration stands, so the screen offers the right
 * doorway — the set-up card before the handshake, the glasses row after it. Plus the one thing
 * Settings can do to the Journal: empty it.
 *
 * Raising the handshake itself is the screen's job, not this class's — Android can only hand off to
 * Meta AI from an Activity, the same un-mirrorable seam
 * [com.meta.pixelandtexel.birdspotter.domain.PermissionsController] documents.
 */
class SettingsViewModel(
    glassesSession: GlassesSessionRepository,
    private val journal: JournalRepository,
    private val mockDevice: MockDeviceRepository,
) : ViewModel() {

  /**
   * Where registration stands — `null` until the first reading lands, and the screen offers nothing
   * about glasses over a `null`: flashing the set-up card at someone who is already linked would be
   * worse than a beat of silence.
   */
  val registrationState: StateFlow<GlassesRegistrationState?> =
      glassesSession
          .registrationStateStream()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  private val _isDeletingJournal = MutableStateFlow(false)

  /**
   * Set while the sweep is running, so the confirmed delete cannot be started twice and the button
   * can say what it is doing.
   */
  val isDeletingJournal: StateFlow<Boolean> = _isDeletingJournal.asStateFlow()

  /**
   * Whether the Mock Device Kit is standing in for the real SDK.
   *
   * **Offered in every registration state, deliberately.** The mock is the thing you reach for
   * *without* glasses, so its switch cannot live behind the glasses row that only registration
   * unlocks — it sits in the Demo / Developer section, which is always there.
   */
  val isMockDeviceEnabled: StateFlow<Boolean> =
      mockDevice
          .isEnabledStream()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), mockDevice.isEnabled)

  private val _isMockDeviceFlipping = MutableStateFlow(false)

  /** Set while the flip is in flight, so the switch cannot be thrown twice into the wait. */
  val isMockDeviceFlipping: StateFlow<Boolean> = _isMockDeviceFlipping.asStateFlow()

  /**
   * Flips the mock. The kit ends any running session first, which is why this takes a beat and why
   * the switch is held during it.
   */
  fun setMockDeviceEnabled(enabled: Boolean) {
    if (_isMockDeviceFlipping.value || enabled == isMockDeviceEnabled.value) return
    _isMockDeviceFlipping.value = true
    viewModelScope.launch {
      try {
        mockDevice.setEnabled(enabled)
      } finally {
        // However the flip ends — a throw from the kit included — the switch is
        // handed back, or it stays greyed out for the life of the screen.
        _isMockDeviceFlipping.value = false
      }
    }
  }

  /**
   * Empties the Journal. Called only from behind the screen's confirmation — nothing here asks a
   * second time.
   *
   * A failure is swallowed on purpose: the Journal is a local store with nothing to retry against,
   * and the screen behind this one is already showing whatever survived.
   */
  fun deleteAllJournalData() {
    if (_isDeletingJournal.value) return
    _isDeletingJournal.value = true
    viewModelScope.launch {
      runCatching { journal.deleteAll() }
      _isDeletingJournal.value = false
    }
  }

  companion object {
    fun factory(
        glassesSession: GlassesSessionRepository,
        journal: JournalRepository,
        mockDevice: MockDeviceRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(glassesSession, journal, mockDevice) as T
          }
        }
  }
}
