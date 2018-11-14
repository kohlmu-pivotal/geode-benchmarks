/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.geode.perftest;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.geode.perftest.infrastructure.InfraManager;
import org.apache.geode.perftest.infrastructure.Infrastructure;
import org.apache.geode.perftest.jvms.JVMManager;
import org.apache.geode.perftest.jvms.RemoteJVMs;

/**
 * Runner that executes a {@link PerformanceTest}, using
 * a provided {@link InfraManager}.
 *
 * This is the main entry point for running tests. Users should
 * implement {@link PerformanceTest} to define there tests in
 * a declarative fashion and then execute them this runner.
 */
public class TestRunner {
  private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);


  private final InfraManager infraManager;
  private final JVMManager jvmManager;

  public TestRunner(InfraManager infraManager, JVMManager jvmManager) {
    this.infraManager = infraManager;
    this.jvmManager = jvmManager;
  }

  public void runTest(PerformanceTest test, int nodes) throws Exception {
    try (Infrastructure infra = infraManager.create(nodes)){
      TestConfig config = new TestConfig();
      test.configure(config);

      Map<String, Integer> roles = config.getRoles();

      logger.info("Lauching JVMs...");
      //launch JVMs in parallel, hook them up
      try (RemoteJVMs remoteJVMs = jvmManager.launch(infra, roles)) {

        logger.info("Starting before tasks...");
        runTasks(config.getBefore(), remoteJVMs);

        logger.info("Starting workload tasks...");
        runTasks(config.getWorkload(), remoteJVMs);

        logger.info("Starting after tasks...");
        runTasks(config.getAfter(), remoteJVMs);

        logger.info("Copying results...");
        File outputDir = new File("output");
        int nodeId = 0;
        for (Infrastructure.Node node : infra.getNodes()) {
          infra.copyFromNode(node, "output", new File(outputDir, "node-" + nodeId++));
        }
      }
    }
  }

  private void runTasks(List<TestConfig.TestStep> steps,
                        RemoteJVMs remoteJVMs) {
    steps.forEach(testStep -> {
      remoteJVMs.execute(testStep.getTask(), testStep.getRoles());
    });
  }
}
