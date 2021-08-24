/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.quickjs.ktbridge

import app.cash.quickjs.ktbridge.OutboundCall.Factory
import kotlin.reflect.KClass
import okio.Buffer

/**
 * This class models a single call sent to another Kotlin platform in the same process.
 *
 * It should be used to help implement an application-layer interface that is implemented by the
 * other platform. Implement each function in that interface to create an [OutboundCall] by calling
 * [Factory.create], pass in each received argument to [parameter], and then call [invoke] to
 * perform the cross-platform call.
 */
@PublishedApi
internal class OutboundCall private constructor(
  private val instanceName: String,
  private val jsAdapter: JsAdapter,
  private val internalBridge: InternalBridge,
  private val funName: String,
  private val parameterCount: Int,
) {
  private val buffer = Buffer()
    .apply {
      writeInt(parameterCount)
    }
  private var callCount = 0
  private val eachParameterBuffer = Buffer()

  inline fun <reified T : Any> parameter(value: T) = parameter(T::class, value)

  fun <T : Any> parameter(type: KClass<T>, value: T) {
    require(callCount++ < parameterCount)
    jsAdapter.encode(value, eachParameterBuffer, type)
    buffer.writeInt(eachParameterBuffer.size.toInt())
    buffer.writeAll(eachParameterBuffer)
  }

  inline fun <reified R : Any> invoke(): R {
    return invoke(R::class)
  }

  fun <R : Any> invoke(type: KClass<R>): R {
    require(callCount++ == parameterCount)
    val encodedArguments = buffer.readByteArray()
    val encodedResponse = internalBridge.invokeJs(instanceName, funName, encodedArguments)
    buffer.write(encodedResponse)
    return jsAdapter.decode(buffer, type)
  }

  class Factory internal constructor(
    private val instanceName: String,
    private val jsAdapter: JsAdapter,
    private val internalBridge: InternalBridge,
  ) {
    fun create(
      funName: String,
      parameterCount: Int
    ): OutboundCall {
      return OutboundCall(instanceName, jsAdapter, internalBridge, funName, parameterCount)
    }
  }
}
