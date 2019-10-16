/*
 * Copyright 2019-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.core.rules.platform;

import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.platform.ConstraintResolver;
import com.facebook.buck.core.model.platform.ConstraintValue;
import com.facebook.buck.core.model.platform.NamedPlatform;
import com.facebook.buck.core.model.platform.PlatformResolver;
import com.facebook.buck.core.model.platform.impl.ConstraintBasedPlatform;
import com.facebook.buck.core.rules.config.ConfigurationRule;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.facebook.buck.core.util.graph.AcyclicDepthFirstPostOrderTraversalWithPayload;
import com.facebook.buck.core.util.graph.CycleException;
import com.facebook.buck.core.util.graph.GraphTraversableWithPayload;
import com.facebook.buck.util.types.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;

public class RuleBasedPlatformResolver implements PlatformResolver {

  private final ConfigurationRuleResolver configurationRuleResolver;
  private final ConstraintResolver constraintResolver;

  public RuleBasedPlatformResolver(
      ConfigurationRuleResolver configurationRuleResolver, ConstraintResolver constraintResolver) {
    this.configurationRuleResolver = configurationRuleResolver;
    this.constraintResolver = constraintResolver;
  }

  @Override
  public NamedPlatform getPlatform(BuildTarget buildTarget) {
    GraphTraversableWithPayload<BuildTarget, PlatformRule> traversable =
        target -> {
          PlatformRule platformRule = getPlatformRule(target);
          return new Pair<>(platformRule, platformRule.getDeps().iterator());
        };

    AcyclicDepthFirstPostOrderTraversalWithPayload<BuildTarget, PlatformRule> platformTraversal =
        new AcyclicDepthFirstPostOrderTraversalWithPayload<>(traversable);

    LinkedHashMap<BuildTarget, PlatformRule> platformTargets;
    try {
      platformTargets = platformTraversal.traverse(ImmutableList.of(buildTarget));
    } catch (CycleException e) {
      throw new HumanReadableException(e.getMessage());
    }

    ImmutableSet<ConstraintValue> constraintValues =
        platformTargets.values().stream()
            .flatMap(rule -> rule.getConstrainValues().stream())
            .map(constraintResolver::getConstraintValue)
            .collect(ImmutableSet.toImmutableSet());

    return new ConstraintBasedPlatform(buildTarget, constraintValues);
  }

  private PlatformRule getPlatformRule(BuildTarget buildTarget) {
    ConfigurationRule configurationRule = configurationRuleResolver.getRule(buildTarget);
    if (!(configurationRule instanceof PlatformRule)) {
      throw new HumanReadableException(
          "%s is used as a target platform, but not declared using `platform` rule",
          buildTarget.getFullyQualifiedName());
    }
    return (PlatformRule) configurationRule;
  }
}
