// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.pubsub.clients.gcloud;

import com.google.cloud.pubsub.PubSub;
import com.google.cloud.pubsub.PubSubException;
import com.google.cloud.pubsub.PubSubOptions;
import com.google.common.base.Preconditions;
import com.google.pubsub.clients.common.LoadTestRunner;
import com.google.pubsub.clients.common.MetricsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a task that consumes messages from a Cloud Pub/Sub subscription.
 */
class CPSSubscriberTask implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(CPSSubscriberTask.class);
  private final String subscription; // set null for Publish
  private final int batchSize;
  private final MetricsHandler metricsHandler;
  private final PubSub pubSub;

  private CPSSubscriberTask(String project, String subscription, int batchSize) {
    this.pubSub = PubSubOptions.builder()
        .projectId(project)
        .build().service();
    this.subscription = Preconditions.checkNotNull(subscription);
    this.metricsHandler = new MetricsHandler(Preconditions.checkNotNull(project), "gcloud");
    this.batchSize = batchSize;
  }

  public static void main(String[] args) throws Exception {
    LoadTestRunner.run(request ->
        new CPSSubscriberTask(request.getProject(), request.getSubscription(), request.getMaxMessagesPerPull()));
  }


  @Override
  public void run() {
    try {
      List<String> ackIds = new ArrayList<>();
      long now = System.currentTimeMillis();
      List<Long> endToEndLatencies = new ArrayList<>();
      pubSub.pull(subscription, batchSize).forEachRemaining((response) -> {
        ackIds.add(response.ackId());
        endToEndLatencies.add(now - Long.parseLong(response.attributes().get("sendTime")));
      });
      endToEndLatencies.stream().distinct().forEach(metricsHandler::recordEndToEndLatency);
      pubSub.ack(subscription, ackIds);
    } catch (PubSubException e) {
      log.error("Error pulling or acknowledging messages.", e);
    }
  }
}