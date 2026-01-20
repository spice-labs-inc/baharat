/*
 * Copyright 2026 Spice Labs, Inc.
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
package io.spicelabs.baharat.rpm.metadata;

/**
 * Enumeration of RPM dependency types.
 */
public enum DependencyType {
    /**
     * Package requires another package or capability.
     */
    REQUIRES,

    /**
     * Package provides a capability.
     */
    PROVIDES,

    /**
     * Package conflicts with another package.
     */
    CONFLICTS,

    /**
     * Package obsoletes another package.
     */
    OBSOLETES,

    /**
     * Package recommends another package (weak dependency).
     */
    RECOMMENDS,

    /**
     * Package suggests another package (weak dependency).
     */
    SUGGESTS,

    /**
     * Package supplements another package (weak dependency).
     */
    SUPPLEMENTS,

    /**
     * Package enhances another package (weak dependency).
     */
    ENHANCES,

    /**
     * Package ordering dependency.
     */
    ORDER
}
