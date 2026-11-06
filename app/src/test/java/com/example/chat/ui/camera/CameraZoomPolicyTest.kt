package com.example.chat.ui.camera
import org.junit.Assert.*
import org.junit.Test
class CameraZoomPolicyTest {
 @Test fun fullDeviceRangeAndOutOfRangeClamp(){assertEquals(30f,clampCameraZoom(100f,.5f,30f),0f);assertEquals(.5f,clampCameraZoom(.1f,.5f,30f),0f);assertEquals(7.5f,clampCameraZoom(7.5f,.5f,30f),0f)}
 @Test fun invalidCapabilityNeverReachesNativeZoom(){assertEquals(1f,clampCameraZoom(Float.NaN,1f,10f),0f);assertEquals(1f,clampCameraZoom(2f,3f,1f),0f)}
 @Test fun disabledFacePipelineDoesNotRequestRasterConversion(){assertFalse(shouldAnalyzeCameraFrame(false,100_000_000,0));assertFalse(shouldAnalyzeCameraFrame(true,50_000_000,0));assertTrue(shouldAnalyzeCameraFrame(true,66_000_000,0))}
}
