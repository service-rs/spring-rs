/*
 * Copyright (c) 2020, Kyle Fricilone <kfricilone@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */

package me.kfricilone.spring.service.web

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import lzma.streams.LzmaOutputStream
import me.kfricilone.spring.service.web.util.BinaryType
import me.kfricilone.spring.service.web.util.ClientContext
import me.kfricilone.spring.service.web.util.ClientLayout
import me.kfricilone.spring.service.web.util.FileContext
import me.kfricilone.spring.service.web.util.LzmaEncoder
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import javax.annotation.PostConstruct

/**
 * Created by Kyle Fricilone on May 14, 2020.
 */
@Component
class ClientResource(
    val config: ClientConfig
) {

    val contexts: MutableMap<BinaryType, ClientContext> = mutableMapOf()

    @PostConstruct
    fun loadClients() {
        runBlocking {
            val jobs = mutableListOf<Deferred<ClientContext>>()

            layouts.forEach {
                jobs.add(async(Dispatchers.IO) { load(it) })
            }

            jobs.forEach {
                val ctx = it.await()
                contexts[ctx.type] = ctx
            }
        }

    }

    private fun load(layout: ClientLayout): ClientContext {
        val crc = CRC32()
        val path = Paths.get(config.clients, layout.sub)
        val files = mutableMapOf<String, FileContext>()
        val config = path.resolve("jav_config.ws").toFile().readBytes()

        layout.client?.let {
            val bytes = path.resolve(it).toFile().readBytes()
            val compressed = if (it.endsWith(".jar")) {
                gzip(bytes)
            } else {
                lzma(bytes)
            }

            crc.reset()
            crc.update(bytes, 0, bytes.size)

            files[it] = FileContext(compressed, crc.value)
        }

        layout.libs.forEach {
            val bytes = Files.readAllBytes(path.resolve(it))
            val compressed = if (it.endsWith(".jar")) {
                bytes
            } else {
                lzma(bytes)
            }

            crc.reset()
            crc.update(bytes, 0, bytes.size)

            files[it] = FileContext(compressed, crc.value)
        }

        return ClientContext(layout.type, config, files)
    }

    private fun lzma(file: ByteArray): ByteArray {

        ByteArrayOutputStream().use { baos ->
            ByteArrayInputStream(file).use { bais ->
                LzmaOutputStream(baos, LzmaEncoder(file.size.toLong())).use {
                    bais.copyTo(it)
                }
            }

            return baos.toByteArray()
        }

    }

    private fun gzip(file: ByteArray): ByteArray {

        ByteArrayOutputStream().use { baos ->
            ByteArrayInputStream(file).use { bais ->
                GZIPOutputStream(baos).use {
                    bais.copyTo(it)
                }
            }

            return baos.toByteArray()
        }

    }

    companion object {

        val layouts: MutableList<ClientLayout> = mutableListOf(
            ClientLayout(BinaryType.WINDOWS_XP, "nxt/client/windows/x86/normal_xp", "rs2client.exe"),
            ClientLayout(BinaryType.WINDOWS_X86, "nxt/client/windows/x86/normal", "rs2client.exe"),
            ClientLayout(BinaryType.WINDOWS_X86_64, "nxt/client/windows/x86_64/normal", "rs2client.exe"),
            ClientLayout(BinaryType.MACINTOSH, "nxt/client/mac", "librs2client.dylib"),
            ClientLayout(BinaryType.LINUX, "nxt/client/linux", "librs2client.so"),
            ClientLayout(
                BinaryType.WINDOWS_X86_COMPAT,
                "nxt/client/windows/x86/compatibility",
                "rs2client.exe",
                mutableListOf("libEGL.dll", "libGLESv2.dll", "D3Dcompiler_43.dll")
            ),
            ClientLayout(
                BinaryType.WINDOWS_X86_64_COMPAT,
                "nxt/client/windows/x86_64/compatibility",
                "rs2client.exe",
                mutableListOf("libEGL.dll", "libGLESv2.dll", "D3Dcompiler_47.dll")
            ),
            ClientLayout(BinaryType.ANDROID, "nxt/client/android"),
            ClientLayout(BinaryType.IOS, "nxt/client/ios"),
            ClientLayout(
                BinaryType.JAVA,
                "jav/client",
                "gamepack.jar",
                mutableListOf("browsercontrol_0.jar", "browsercontrol_1.jar")
            )
        )

    }

}