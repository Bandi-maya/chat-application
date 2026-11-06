package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent as composeSetContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext

/** Empty synthetic host: no ServiceLocator, auth/session, preferences or backend startup.
 *
 *  Recreation safeguard (D-call59): in run 42036821886 the emulator relaunched
 *  this host ~1s after creation, same process, trigger unproven, and the
 *  replacement instance stayed blank because tests install content exactly
 *  once via ActivityScenario.onActivity. The member setContent below shadows
 *  the androidx.activity.compose extension at every existing call site
 *  (members win over extensions in Kotlin resolution, so test sources stay
 *  byte-identical): it records the latest content in the companion, which
 *  survives same-process recreation, and onCreate re-applies it on the new
 *  instance. The holder is cleared when the activity finishes (scenario
 *  close), so a later scenario can never resurrect a previous test's content.
 *  No retry-to-pass: the relaunch itself is only logged, never hidden. */
class MediaFixtureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recorded = fixtureContent
        if (recorded != null) {
            android.util.Log.d("MediaFixtureActivity", "recreated; re-installing recorded fixture content")
            setContent(content = recorded)
        } else {
            android.util.Log.d("MediaFixtureActivity", "created with no recorded fixture content")
        }
    }

    override fun onDestroy() {
        if (isFinishing) fixtureContent = null
        super.onDestroy()
    }

    /** Records the latest installed [content] for re-install after recreation,
     *  then delegates to the real androidx.activity.compose.setContent extension
     *  (imported under the composeSetContent alias so the member cannot shadow it). */
    fun setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {
        fixtureContent = content
        this.composeSetContent(parent, content)
    }

    companion object {
        private var fixtureContent: (@Composable () -> Unit)? = null
    }
}
