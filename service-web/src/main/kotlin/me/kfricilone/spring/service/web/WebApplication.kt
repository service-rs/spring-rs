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

import com.google.protobuf.ByteString
import me.kfricilone.spring.api.messages.web.WebServiceCoroutineGrpc
import me.kfricilone.spring.api.messages.web.client.WebClientRequest
import me.kfricilone.spring.api.messages.web.client.WebConfigRequest
import me.kfricilone.spring.api.messages.web.server.WebClientResponse
import me.kfricilone.spring.api.messages.web.server.WebConfigResponse
import me.kfricilone.spring.service.web.util.BinaryType
import org.lognet.springboot.grpc.GRpcService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

/**
 * Created by Kyle Fricilone on Jul 28, 2020.
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
class WebApplication {

	@GRpcService
	class WebService : WebServiceCoroutineGrpc.WebServiceImplBase() {

		@Autowired
		lateinit var clients: ClientResource

		override suspend fun getClient(request: WebClientRequest): WebClientResponse {
			val builder = WebClientResponse.newBuilder()
				.setType(request.type)

			val type = BinaryType.valueOf(request.type)
			clients.contexts[type]?.let { ctx ->
				ctx.getFile(request.name, request.crc)?.let {
					builder.setClient(ByteString.copyFrom(it))
				}
			}

			return builder.build()
		}

		override suspend fun getConfig(request: WebConfigRequest): WebConfigResponse {
			val builder = WebConfigResponse.newBuilder()
				.setType(request.type)

			val type = BinaryType.valueOf(request.type)
			clients.contexts[type]?.let {
				builder.setConfig(ByteString.copyFrom(it.config))
			}

			return builder.build()
		}
	}

}

fun main(args: Array<String>) {
	runApplication<WebApplication>(*args)
}