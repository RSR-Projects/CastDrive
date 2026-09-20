package com.example

import com.example.model.AppRole
import com.example.model.ConnectionType
import com.example.model.QualityPreset
import com.example.model.ScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testQualityPresets() {
    val presets = QualityPreset.values()
    assertEquals(3, presets.size)

    val ultraFast = QualityPreset.ULTRA_FAST
    assertEquals(60, ultraFast.frameRate)
    assertEquals(960, ultraFast.targetWidth)
    assertEquals(540, ultraFast.targetHeight)

    val balanced = QualityPreset.BALANCED
    assertEquals(1280, balanced.targetWidth)
    assertEquals(720, balanced.targetHeight)
  }

  @Test
  fun testConnectionTypes() {
    assertEquals("USB Tethering", ConnectionType.USB_TETHERING.title)
    assertEquals("Wi-Fi Hotspot", ConnectionType.WIFI_HOTSPOT.title)
  }

  @Test
  fun testAppRoles() {
    val roles = AppRole.values()
    assertEquals(4, roles.size)
  }

  @Test
  fun testScaleModes() {
    val modes = ScaleMode.values()
    assertEquals(3, modes.size)
  }
}
