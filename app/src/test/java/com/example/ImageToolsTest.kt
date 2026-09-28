package com.example

import android.net.Uri
import com.example.data.util.ImageTextRecognizer
import com.example.ui.screens.image.*
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageToolsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createFakeText(text: String): Text {
        // Find a constructor that we can invoke via reflection
        for (constructor in Text::class.java.declaredConstructors) {
            constructor.isAccessible = true
            val params = constructor.parameterTypes
            try {
                val args = params.map { paramType ->
                    when {
                        paramType == String::class.java -> text
                        paramType == List::class.java -> emptyList<Any>()
                        paramType.isPrimitive -> {
                            if (paramType == Boolean::class.javaPrimitiveType) false else 0
                        }
                        else -> null
                    }
                }.toTypedArray()
                return constructor.newInstance(*args) as Text
            } catch (e: Exception) {
                // Keep trying other constructors if they fail
            }
        }
        throw IllegalStateException("Could not instantiate Text class via reflection")
    }

    @Test
    fun `ImageToTextOcrViewModel recognizes text and updates state`() = runTest {
        val fakeRecognizer = object : ImageTextRecognizer(RuntimeEnvironment.getApplication()) {
            override suspend fun recognizeText(imageUri: Uri): Text {
                return createFakeText("Hello World")
            }
        }

        val viewModel = ImageToTextOcrViewModel(fakeRecognizer)
        val uri = Uri.parse("content://media/external/images/media/1")

        viewModel.processImage(uri)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OcrUiState.Success)
        assertEquals("Hello World", (viewModel.uiState.value as OcrUiState.Success).originalText)
    }
}
