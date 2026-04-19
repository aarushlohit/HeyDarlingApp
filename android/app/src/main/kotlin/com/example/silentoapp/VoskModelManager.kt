package com.example.silentoapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object VoskModelManager {
    fun ensureModelAvailable(
        context: Context,
        assetPath: String,
        logger: SilentAssistantLogger,
    ): File {
        val modelName = assetPath.substringAfterLast('/')
        val destination = File(context.filesDir, modelName)
        val sentinel = File(destination, "am/final.mdl")

        if (sentinel.exists()) {
            logger.log("Using cached Vosk model at ${destination.absolutePath}")
            return destination
        }

        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()

        copyAssetFolder(context, assetPath, destination)

        if (!sentinel.exists()) {
            throw IOException(
                "Vosk model is missing from android/app/src/main/assets/$assetPath. " +
                    "Add the extracted offline model before running on Android.",
            )
        }

        logger.log("Copied Vosk model to ${destination.absolutePath}")
        return destination
    }

    private fun copyAssetFolder(context: Context, assetPath: String, outputDir: File) {
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            copyAssetFile(context, assetPath, outputDir)
            return
        }

        for (entry in entries) {
            val sourcePath = "$assetPath/$entry"
            val childEntries = context.assets.list(sourcePath).orEmpty()
            if (childEntries.isEmpty()) {
                copyAssetFile(context, sourcePath, File(outputDir, entry))
            } else {
                val childDir = File(outputDir, entry)
                childDir.mkdirs()
                copyAssetFolder(context, sourcePath, childDir)
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
