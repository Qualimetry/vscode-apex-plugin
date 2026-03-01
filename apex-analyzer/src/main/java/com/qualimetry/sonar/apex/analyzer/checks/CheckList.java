/*
 * Copyright 2026 SHAZAM Analytics Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qualimetry.sonar.apex.analyzer.checks;

import com.qualimetry.sonar.apex.analyzer.visitor.BaseCheck;
import org.sonar.check.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of all Apex analysis checks. Default profile uses {@link #getDefaultRuleKeys()}.
 */
public final class CheckList {

    public static final String REPOSITORY_KEY = "qualimetry-apex";
    public static final String REPOSITORY_NAME = "Qualimetry Apex";
    public static final String LANGUAGE_KEY = "apex";

    private CheckList() {
    }

    public static List<Class<? extends BaseCheck>> getAllChecks() {
        return List.of(
                ApexSyntaxCheck.class
        );
    }

    /** Rule keys enabled in the default Qualimetry Apex profile. */
    public static List<String> getDefaultRuleKeys() {
        return List.of(
                "qa-apex-syntax"
        );
    }

    /** All rule keys from every check. */
    public static List<String> getAllRuleKeys() {
        List<String> keys = new ArrayList<>();
        for (Class<? extends BaseCheck> clazz : getAllChecks()) {
            Rule r = clazz.getAnnotation(Rule.class);
            if (r != null && r.key() != null && !r.key().isEmpty()) {
                keys.add(r.key());
            }
        }
        return List.copyOf(keys);
    }
}
