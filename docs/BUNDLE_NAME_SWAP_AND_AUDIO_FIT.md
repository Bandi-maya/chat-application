# Stage10: bundle labels and audio control fit

Stable IDs never swap. apple_ios is now displayed as Chaty Default with Default badge and first in the picker; chaty_default is displayed as Apple iOS Call Experience. Existing incoming duplicate is corrected: chaty_default restores ChatyDefaultIncomingCall, apple_ios retains AppleIosIncomingCall. Ongoing/message families keep their IDs. Fresh/missing calls default to apple_ios; explicit saved IDs are never rewritten. Messages remain original/unselected until Apply.

This corrects an unreleased stage5 mistake; it does not preserve the accidental duplicated incoming screen. It is not all-screen visual parity work.

Audio control fix: measure width after panel padding, calculate explicit cell widths with <=bundle preferred columns and76dpminimum cell budget, avoid intrinsic label widths forcing a fourth row. Large text wraps within its cell, controls remain >=48dp. Vertical spacing bounded12dp; audio panel can use65%screen vs48%video, still scrollable. Only Mute/Speaker/Video/Hang up are primary. Runtime proof still required at220dp/1.5x/2x and light/dark across nine bundles.

Existing local matrix source still expected End call in two voice tests. This delta changes those assertions to Hang up. Publisher has a NEWER matrix method absent here (allBundlesIncomingAndAudioNarrowLargeTextLightDark); do NOT replace or drop it. Merge these two assertion changes into newer instrumentation file and retain all newer methods/gates. Production audio semantic name already Hang up; no alias or test relaxation added.

JVM checks:4actual preference store tests incl missing defaults and saved stable IDs;3geometry profile tests+1responsive column test (enum stub seam). No actual Android compile/pixels in this writer. Require fresh combined strict run and visual gate, no APK release.
