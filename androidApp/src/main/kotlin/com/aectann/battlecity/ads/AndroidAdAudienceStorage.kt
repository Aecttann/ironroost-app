package com.aectann.battlecity.ads

import android.content.Context
import android.util.AtomicFile
import com.aectann.battlecity.TanksAdAudienceStorage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class AndroidAdAudienceStorage(context: Context) : TanksAdAudienceStorage {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "ad_audience"))

    override fun read(): String? = synchronized(StorageLock) { readFile() }

    private fun readFile(): String? = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists()) throw error else null
    }

    override fun getOrPut(value: String): String = synchronized(StorageLock) {
        readFile()?.let { return@synchronized it }
        val output = file.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: IOException) {
            file.failWrite(output)
            throw error
        }
        if (readFile() != value) throw IOException("Ad audience was not persisted")
        value
    }

    private companion object {
        val StorageLock = Any()
    }
}
