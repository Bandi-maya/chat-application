package com.example.chat.ui.camera

/** Exact port of the logic half of lib/features/camera/effects/effect_engine.dart.
 *
 *  Camera Effects Engine state: active effect, intensity, performance
 *  fallback, recents and favorites. The rendering half (ColorFiltered +
 *  animated particle overlay) is ported in CameraEffectRender.kt as Compose;
 *  this class is context-free and JVM-testable.
 */
class EffectEngine {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    /** ChangeNotifier.dispose: release listeners. */
    fun dispose() { listeners.clear() }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var _activeEffect: ChatyCameraEffect = EffectRegistry.allEffects.first()
    private var _intensity = 1.0
    private var _isEngineDisabledForPerformance = false
    private val _recentEffectIds = mutableListOf<String>()
    private val _favoriteEffectIds = mutableSetOf<String>()

    val activeEffect: ChatyCameraEffect
        get() = if (_isEngineDisabledForPerformance) {
            EffectRegistry.allEffects.first()
        } else {
            _activeEffect
        }

    val intensity: Double get() = _intensity
    val isEngineDisabled: Boolean get() = _isEngineDisabledForPerformance
    val recentEffectIds: List<String> get() = _recentEffectIds.toList()
    val favoriteEffectIds: Set<String> get() = _favoriteEffectIds.toSet()

    fun selectEffect(effect: ChatyCameraEffect) {
        _activeEffect = effect
        _intensity = effect.defaultIntensity

        if (effect.id != "none") {
            _recentEffectIds.remove(effect.id)
            _recentEffectIds.add(0, effect.id)
            if (_recentEffectIds.size > 20) {
                _recentEffectIds.removeAt(_recentEffectIds.size - 1)
            }
        }
        notifyListeners()
    }

    fun setIntensity(value: Double) {
        _intensity = value.coerceIn(0.0, 1.0)
        notifyListeners()
    }

    fun toggleFavorite(effectId: String) {
        if (!_favoriteEffectIds.add(effectId)) {
            _favoriteEffectIds.remove(effectId)
        }
        notifyListeners()
    }

    /** Automatically disables heavy GPU filters if device thermal/frame rate drops */
    fun triggerPerformanceFallback() {
        if (!_isEngineDisabledForPerformance) {
            _isEngineDisabledForPerformance = true
            notifyListeners()
        }
    }

    fun restorePerformanceMode() {
        if (_isEngineDisabledForPerformance) {
            _isEngineDisabledForPerformance = false
            notifyListeners()
        }
    }

    /** Whether the animated AR overlay (celebration confetti / face stickers)
     *  stacks over the content for the active effect. Mirrors the Dart
     *  renderEffect condition. */
    val isAnimatedOverlayActive: Boolean
        get() = !_isEngineDisabledForPerformance &&
            _activeEffect.id != "none" &&
            (_activeEffect.category == EffectCategory.celebration ||
                _activeEffect.id == "face_sparkles" ||
                _activeEffect.id == "face_hearts_float" ||
                _activeEffect.id == "face_dog_filter" ||
                _activeEffect.id == "face_neon_crown")

    /** The color matrix to apply for the active effect at the current
     *  intensity, or null when the content renders unchanged. Mirrors the
     *  Dart renderEffect color-matrix branch. */
    fun activeColorMatrix(): List<Double>? {
        if (_isEngineDisabledForPerformance || _activeEffect.id == "none") return null
        val matrix = _activeEffect.colorMatrix ?: return null
        return interpolateMatrix(matrix, _intensity)
    }

    companion object {
        private val IDENTITY = listOf(
            1.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 1.0, 0.0,
        )

        /** Exact port of EffectEngine._interpolateMatrix: identity lerped
         *  toward target by intensity. */
        fun interpolateMatrix(target: List<Double>, intensity: Double): List<Double> {
            val result = MutableList(20) { 0.0 }
            for (i in 0 until 20) {
                result[i] = IDENTITY[i] + (target[i] - IDENTITY[i]) * intensity
            }
            return result
        }
    }
}
