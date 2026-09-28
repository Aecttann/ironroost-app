package com.aectann.battlecity.ads

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aectann.battlecity.TanksAdAudience
import com.aectann.battlecity.TanksAdAudienceRepository
import com.aectann.battlecity.adAudienceForAge
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdAudienceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TanksAdAudienceRepository(AndroidAdAudienceStorage(application))

    var audience by mutableStateOf<TanksAdAudience?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            audience = try {
                withContext(Dispatchers.IO) { repository.currentAudience() }
            } catch (error: IOException) {
                Log.w(Tag, "Cannot read ad audience; applying protected treatment", error)
                TanksAdAudience.MinorOrUnknown
            }
            isLoading = false
        }
    }

    fun chooseAge(age: Int?) {
        if (isLoading || isSaving || audience != null) return
        isSaving = true
        saveFailed = false
        val selectedAudience = adAudienceForAge(age)
        viewModelScope.launch {
            try {
                audience = withContext(Dispatchers.IO) { repository.select(selectedAudience) }
            } catch (error: IOException) {
                Log.w(Tag, "Cannot persist ad audience", error)
                saveFailed = true
            } finally {
                isSaving = false
            }
        }
    }

    private companion object {
        const val Tag = "BattleCityAds"
    }
}
