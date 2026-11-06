package com.example.chat
import com.example.chat.ui.profile.*
import com.example.chat.data.api.*
import com.example.chat.domain.models.UserProfile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class ProfileEditorSaveSessionTest {
    private fun scope()=ApiAccountScope("u","fake","t","https://fixture.invalid","",1)
    private class Ops(var current:ApiAccountScope):ProfileEditorOperations {
        var uploads=0;var saves=0;var failSave=false;var onUpload:()->Unit={};var avatar:String?=null
        override fun snapshot()=current
        override fun user():UserProfile?=null
        override fun addListener(listener:(ApiAccountScope)->Unit){}
        override fun removeListener(listener:(ApiAccountScope)->Unit){}
        override suspend fun upload(expected:ApiAccountScope,bytes:ByteArray):String {assertSame(current,expected);uploads++;onUpload();return "https://fixture.invalid/avatar"}
        override suspend fun save(expected:ApiAccountScope,name:String,username:String,about:String,avatarUrl:String?){assertSame(current,expected);saves++;avatar=avatarUrl;if(failSave)error("Synthetic save failure")}
    }
    private fun draft()=ProfileAvatarDraft(File.createTempFile("avatar", ".png").apply{writeBytes(byteArrayOf(1,2))})
    @Test fun pickingAndDiscardNeverPost()=runBlocking {
        val ops=Ops(scope());val session=ProfileEditorSaveSession(ops.current,ops){it};val d=draft()
        session.replace(d);assertEquals(0,ops.uploads);assertEquals(0,ops.saves);d.close();assertEquals(0,ops.uploads)
    }
    @Test fun partialSaveWarnsAndRetryDoesNotReupload()=runBlocking {
        val ops=Ops(scope());val s=ProfileEditorSaveSession(ops.current,ops){it};val d=draft();s.replace(d);ops.failSave=true
        val failure=runCatching{s.save("N","user","about",null)}.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("Discard will not undo"));assertEquals(1,ops.uploads)
        ops.failSave=false;s.save("N","user","about",null);assertEquals(1,ops.uploads);assertEquals(2,ops.saves)
        assertEquals("https://fixture.invalid/avatar",ops.avatar);d.close()
    }
    @Test fun scopeChangeBeforeSaveAndAfterUploadNeverPublishes()=runBlocking {
        val a=scope();val ops=Ops(a);val s=ProfileEditorSaveSession(a,ops){it};val d=draft();s.replace(d)
        ops.current=scope();assertTrue(runCatching{s.save("N","u","a",null)}.exceptionOrNull() is ApiScopeChangedException);assertEquals(0,ops.uploads)
        ops.current=a;ops.onUpload={ops.current=scope()}
        assertTrue(runCatching{s.save("N","u","a",null)}.exceptionOrNull() is ApiScopeChangedException)
        assertEquals(0,ops.saves);d.close()
    }
}
