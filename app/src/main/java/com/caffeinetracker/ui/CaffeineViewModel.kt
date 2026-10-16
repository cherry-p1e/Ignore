package com.caffeinetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caffeinetracker.data.DoseRepository
import com.caffeinetracker.pk.Dose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CaffeineViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DoseRepository.create(app)

    /** Null until the first read, so a restart does not flash an empty glass. */
    val doses: StateFlow<List<Dose>?> = repository.doses.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    fun addDose(amountMg: Int) {
        if (amountMg !in 1..9_999) return
        viewModelScope.launch { repository.addDose(amountMg) }
    }
}
