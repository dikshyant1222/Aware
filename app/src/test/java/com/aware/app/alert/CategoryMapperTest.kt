package com.aware.app.alert

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMapperTest {
    @Test
    fun everyMappedNameExistsInTheRealModel() {
        val model = System.getProperty("aware.yamnet")?.let(::File)
            ?: listOf(File("src/main/assets/yamnet.tflite"), File("app/src/main/assets/yamnet.tflite"))
                .firstOrNull { it.isFile }
        assertNotNull("Download yamnet.tflite before running this test; no fixture is substituted.", model)
        val labels = ZipFile(model!!).use { zip ->
            val entry = zip.getEntry("yamnet_label_list.txt")
            assertNotNull("The model must contain its real label list.", entry)
            zip.getInputStream(entry).bufferedReader().use { it.readLines().toSet() }
        }
        assertEquals(521, labels.size)
        val missing = CategoryMapper.mappings.keys - labels
        assertTrue("Mapped names missing from yamnet.tflite: $missing", missing.isEmpty())
    }

    @Test
    fun everySoundTypeHasAMappingAndAValidThreshold() {
        assertEquals(SoundType.entries.toSet(), CategoryMapper.mappings.values.toSet())
        SoundType.entries.forEach { assertTrue(CategoryMapper.minimumScore(it) in 0f..1f) }
    }

    @Test
    fun exactVariantsMapWithoutGuessingBroadOrUnrelatedLabels() {
        assertEquals(SoundType.SIREN, CategoryMapper.map("Fire engine, fire truck (siren)"))
        assertEquals(SoundType.SPEECH, CategoryMapper.map("Child speech, kid speaking"))
        assertEquals(SoundType.CAR_HORN, CategoryMapper.map("Vehicle horn, car horn, honking"))
        assertEquals(SoundType.DOG_BARK, CategoryMapper.map("Bow-wow"))
        listOf("Alarm", "Glass", "Tap", "Telephone", "Microwave oven", "Music", "Honk", "siren")
            .forEach { assertNull(CategoryMapper.map(it)) }
    }
}
