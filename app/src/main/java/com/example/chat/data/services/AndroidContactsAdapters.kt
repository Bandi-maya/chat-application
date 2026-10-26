package com.example.chat.data.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.chat.ui.chats.detail.ContactDetails

/** Android contacts helpers backing the detail package's ContactsPort for Dart
 *  chat_attachment_actions.dart shareContact():
 *   - FlutterContacts.permissions.request(PermissionType.read) maps onto the
 *     READ_CONTACTS runtime permission (manifest entry required; Android has no
 *     "limited" contacts access, so the result is granted or denied).
 *   - FlutterContacts.native.showPicker maps onto the system contact picker
 *     (ActivityResultContracts.PickContact); [readPickedContact] extracts the
 *     contact id + display name from the returned lookup URI.
 *   - FlutterContacts.get(id, properties: phone, email) maps onto
 *     [fetchContactDetails]: display name plus trimmed, non-empty phone numbers
 *     and email addresses (the Dart controller applies its own take(5)/take(3)
 *     and name fallback when building the message body).
 *  The picker itself can return a contact WITHOUT read permission (user-granted
 *  per-pick access); in that case only the display name is available, matching
 *  Dart's canRead=false path. */
object AndroidContactsAccess {

    fun hasReadAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Extracts (contactId, displayName) from a PickContact result URI.
     *  DISPLAY_NAME is readable from the picker grant; the id lets a later
     *  permission-backed fetch retrieve phone/email details. */
    fun readPickedContact(context: Context, uri: Uri): Pair<String?, String?> {
        var id: String? = null
        var name: String? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) id = c.getString(0)
                    if (!c.isNull(1)) name = c.getString(1)
                }
            }
        } catch (_: SecurityException) {
            // No read access: fall through with whatever the picker exposed.
        }
        return Pair(id, name)
    }

    /** FlutterContacts.get(id, properties: {phone, email}): display name +
     *  all phone numbers + all email addresses for the contact. Returns null
     *  when the contact cannot be read (no permission or unknown id). */
    fun fetchContactDetails(context: Context, id: String): ContactDetails? {
        if (!hasReadAccess(context)) return null
        val resolver = context.contentResolver
        var name: String? = null
        try {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(id),
                null,
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
        } catch (_: SecurityException) {
            return null
        }
        val phones = mutableListOf<String>()
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(id),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (!c.isNull(0)) phones.add(c.getString(0))
                }
            }
        } catch (_: SecurityException) {
            return null
        }
        val emails = mutableListOf<String>()
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(id),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (!c.isNull(0)) emails.add(c.getString(0))
                }
            }
        } catch (_: SecurityException) {
            return null
        }
        return ContactDetails(displayName = name, phones = phones, emails = emails)
    }
}
