/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.saga.spring;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.servicecomb.saga.core.JacksonToJsonFormat;
import io.servicecomb.saga.core.PersistentStore;
import io.servicecomb.saga.core.SagaDefinition;
import io.servicecomb.saga.core.ToJsonFormat;
import io.servicecomb.saga.core.application.SagaExecutionComponent;
import io.servicecomb.saga.core.application.interpreter.FromJsonFormat;
import io.servicecomb.saga.core.dag.GraphBasedSagaFactory;
import io.servicecomb.saga.format.ChildrenExtractor;
import io.servicecomb.saga.format.JacksonFromJsonFormat;
import io.servicecomb.saga.format.JacksonSagaEventFormat;
import io.servicecomb.saga.format.SagaEventFormat;
import io.servicecomb.saga.transports.RestTransport;
import io.servicecomb.saga.transports.TransportFactory;

@Configuration
class SagaSpringConfig {

  @Bean
  TransportFactory transportFactory(RestTransport restTransport) {
    return () -> restTransport;
  }

  @Bean
  FromJsonFormat<SagaDefinition> fromJsonFormat(TransportFactory transportFactory) {
    return new JacksonFromJsonFormat(transportFactory);
  }

  @Bean
  ToJsonFormat toJsonFormat() {
    return new JacksonToJsonFormat();
  }

  @Bean
  SagaEventFormat sagaEventFormat(TransportFactory transportFactory) {
    return new JacksonSagaEventFormat(transportFactory);
  }

  @Bean
  PersistentStore persistentStore(SagaEventRepo repo, ToJsonFormat toJsonFormat, SagaEventFormat eventFormat) {
    return new JpaPersistentStore(repo, toJsonFormat, eventFormat);
  }

  @Bean
  SagaExecutionQueryService queryService(SagaEventRepo repo, FromJsonFormat<SagaDefinition> fromJsonFormat) {
    return new SagaExecutionQueryService(repo, fromJsonFormat);
  }

  @Bean
  SagaExecutionComponent sagaExecutionComponent(
      @Value("${saga.thread.count:5}") int numberOfThreads,
      @Value("${saga.retry.delay:3000}") int retryDelay,
      PersistentStore persistentStore,
      ToJsonFormat format,
      FromJsonFormat<SagaDefinition> fromJsonFormat) {

    return new SagaExecutionComponent(
        persistentStore,
        fromJsonFormat,
        format,
        new GraphBasedSagaFactory(
            retryDelay,
            persistentStore,
            new ChildrenExtractor(),
            Executors.newFixedThreadPool(numberOfThreads, sagaThreadFactory())));
  }

  private ThreadFactory sagaThreadFactory() {
    return new ThreadFactory() {
      private final AtomicInteger threadCount = new AtomicInteger();

      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "saga-pool-thread-" + threadCount.incrementAndGet());
      }
    };
  }
}
