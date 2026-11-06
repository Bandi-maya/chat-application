package com.example.chat
import android.os.Bundle
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.chat.data.api.*
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.profile.*
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.core.theme.ThemePresets
import java.io.File
import java.time.Instant

/** Actual production scoped host. Only injected operations/picker are synthetic; no ServiceLocator mutations. */
class ProfileHostFixtureActivity:ComponentActivity() {
    @Volatile var uploads=0;@Volatile var saves=0;@Volatile var picks=0;@Volatile var backs=0
    val uploadEntered = java.util.concurrent.CountDownLatch(1)
    val releaseUpload = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val listeners=mutableSetOf<(ApiAccountScope)->Unit>()
    private fun scope()=ApiAccountScope("fixture","fake","fixture","https://fixture.invalid","",1)
    private var current=scope()
    fun changeScope(){current=scope();listeners.toList().forEach{it(current)}}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val user=UserProfile("fixture","Fixture User","fixture_user","FU","0xFF6366F1","Synthetic",lastSeenAt=Instant.EPOCH)
        val ops=object:ProfileEditorOperations {
            override fun snapshot()=current
            override fun user()=user
            override fun addListener(listener:(ApiAccountScope)->Unit){listeners+=listener}
            override fun removeListener(listener:(ApiAccountScope)->Unit){listeners-=listener}
            override suspend fun upload(expected:ApiAccountScope,bytes:ByteArray):String{check(expected===current);uploads++;uploadEntered.countDown();if(intent.getBooleanExtra("holdUpload",false))releaseUpload.await();return "https://fixture.invalid/avatar"}
            override suspend fun save(expected:ApiAccountScope,name:String,username:String,about:String,avatarUrl:String?){check(expected===current);saves++;if(intent.getBooleanExtra("failFirst",false)&&saves==1)error("Synthetic details failure")}
        }
        setContent{ChatyTheme(ThemePresets.getSystemDefaultTheme(platformIsLight=true)){
            ScopedProfileEditorHost(ops,{backs++},picker={currentCheck->
                val draft=remember{mutableStateOf<ProfileAvatarDraft?>(null)}
                DisposableEffect(Unit){onDispose{draft.value?.close()}}
                ProfileAvatarDraftFlow(pick={
                    check(currentCheck());picks++
                    val f=File(cacheDir,"fixture_profile_$picks.png")
                    val b=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.BLUE)
                    f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
                    ProfileAvatarDraft(f).also{draft.value?.close();draft.value=it}
                },discard={draft.value?.close();draft.value=null})
            },compress={it})
        }}
    }
}
