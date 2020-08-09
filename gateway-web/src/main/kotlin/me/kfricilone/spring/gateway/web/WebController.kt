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

package me.kfricilone.spring.gateway.web

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import me.kfricilone.spring.api.messages.js5.Js5ServiceCoroutineGrpc
import me.kfricilone.spring.api.messages.web.WebServiceCoroutineGrpc
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.HttpHeaders.CONTENT_ENCODING
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpHeaders.SERVER
import org.springframework.http.HttpHeaders.SET_COOKIE
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Optional
import java.util.UUID

/**
 * Created by Kyle Fricilone on Jul 28, 2020.
 */
@RestController
class WebController(
    val client: LoadBalancerClient
) {

    @GetMapping("/config.ws")
    suspend fun config(
        @RequestParam binaryType: Optional<Int>,
        @CookieValue(name = COOKIE_ID, required = false) webuid: Optional<String>
    ): ResponseEntity<ByteArray> {

        val response = web(client.choose("grpc-spring-service-web"))
            .getConfig { type = binaryType.orElse(-1) }

        val cookie = ResponseCookie.from(COOKIE_ID, webuid.orElse(uid()))
            .path("/")
            .build()

        val headers = HttpHeaders().apply {
            set(SERVER, JAGEX_SERVER)
            set(CONTENT_TYPE, CONFIG_TYPE)
            set(SET_COOKIE, cookie.toString())
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(response.config.toByteArray())

    }

    @GetMapping("/client")
    suspend fun client(
        @RequestParam binaryType: Int,
        @RequestParam fileName: String,
        @RequestParam crc: Int,
        @CookieValue(name = COOKIE_ID, required = false) webuid: Optional<String>
    ): ResponseEntity<ByteArray> {

        println(webuid)

        val response = web(client.choose("grpc-spring-service-web"))
            .getClient {
                type = binaryType
                name = fileName
                setCrc(crc.toLong())
            }

        val cookie = ResponseCookie.from(COOKIE_ID, webuid.orElse(uid()))
            .path("/")
            .build()

        val headers = HttpHeaders().apply {
            set(SERVER, JAGEX_SERVER)
            set(CONTENT_TYPE, APPLICATION_OCTET_STREAM_VALUE)
            set(CONTENT_DISPOSITION, "filename=$fileName")
            set(CONTENT_ENCODING, CLIENT_ENCODING)
            set(SET_COOKIE, cookie.toString())
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(response.client.toByteArray())

    }

    @GetMapping("/ms")
    suspend fun ms(
        @RequestParam m: Int,
        @RequestParam a: Int,
        @RequestParam g: Int,
        @RequestParam cb: Optional<Long>,
        @RequestParam c: Optional<Int>,
        @RequestParam v: Optional<Int>
    ): ResponseEntity<ByteArray> {

        val resp = js5(client.choose("grpc-spring-service-js5"))
            .getFile {
                archive = a
                group = g
            }

        val headers = HttpHeaders().apply {
            set(SERVER, JAGEX_SERVER)
            set(CONTENT_TYPE, APPLICATION_OCTET_STREAM_VALUE)
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(resp.file.toByteArray())

    }

    private suspend fun js5(instance: ServiceInstance): Js5ServiceCoroutineGrpc.Js5ServiceCoroutineStub {
        val channel = ManagedChannelBuilder.forTarget("${instance.host}:${instance.port}")
            .executor(Dispatchers.IO.asExecutor())
            .usePlaintext()
            .build()

        return Js5ServiceCoroutineGrpc.newStubWithContext(channel)
    }

    private suspend fun web(instance: ServiceInstance): WebServiceCoroutineGrpc.WebServiceCoroutineStub {
        val channel = ManagedChannelBuilder.forTarget("${instance.host}:${instance.port}")
            .executor(Dispatchers.IO.asExecutor())
            .usePlaintext()
            .build()

        return WebServiceCoroutineGrpc.newStubWithContext(channel)
    }

    companion object {

        private const val JAGEX_SERVER = "JAGeX/3.1"
        private const val CLIENT_ENCODING = "lzma"
        private const val COOKIE_ID = "JXWEBUID"
        private val CONFIG_TYPE = MediaType(TEXT_PLAIN, Charsets.ISO_8859_1).toString()

        private fun uid() = UUID.randomUUID().toString()

    }

}