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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Optional

/**
 * Created by Kyle Fricilone on Jul 28, 2020.
 */
@RestController
class WebController(
    val client: LoadBalancerClient
) {

    @GetMapping("/config.ws")
    suspend fun config(
        @RequestParam(name = "binaryType") type: Optional<Int>
    ): ResponseEntity<ByteArray> {

        val response = web(client.choose("grpc-spring-service-web"))
            .getConfig { setType(type.orElse(-1)) }

        val headers = HttpHeaders().apply {
            set("Server", "JAGeX/3.1")
            set("Content-Type", "text/plain; charset=ISO-8859-1")
        }

        return ResponseEntity.ok().headers(headers).body(response.config.toByteArray())

    }

    @GetMapping("/client")
    suspend fun client(
        @RequestParam(name = "binaryType") type: Int,
        @RequestParam(name = "fileName") name: String,
        @RequestParam crc: Int
    ): ResponseEntity<ByteArray> {

        val response = web(client.choose("grpc-spring-service-web"))
            .getClient {
                setType(type)
                setName(name)
                setCrc(crc.toLong())
            }

        val headers = HttpHeaders().apply {
            set("Server", "JAGeX/3.1")
            set("Content-Type", "application/octet-stream")
            set("Content-Disposition", "filename=$name")
            set("Content-Encoding", "lzma")
        }

        return ResponseEntity.ok().headers(headers).body(response.client.toByteArray())

    }

    @GetMapping("/ms")
    suspend fun ms(
        @RequestParam(name = "m") mode: Int,
        @RequestParam(name = "a") archive: Int,
        @RequestParam(name = "g") group: Int,
        @RequestParam(name = "cb") timestamp: Optional<Long>,
        @RequestParam(name = "c") crc: Optional<Int>,
        @RequestParam(name = "v") version: Optional<Int>
    ): ResponseEntity<ByteArray> {

        val resp = js5(client.choose("grpc-spring-service-js5"))
            .getFile {
                setArchive(archive)
                setGroup(group)
            }

        val headers = HttpHeaders().apply {
            set("Server", "JAGeX/3.1")
            set("Content-Type", "application/octet-stream")
        }

        return ResponseEntity.ok().headers(headers).body(resp.file.toByteArray())

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

}