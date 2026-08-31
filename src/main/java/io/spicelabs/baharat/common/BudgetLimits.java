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
package io.spicelabs.baharat.common;

import org.jetbrains.annotations.NotNull;

/**
 * Resource budgets for package parsing.
 *
 * <p>Defaults: 2 GiB decompressed data per read pass (loud
 * rejection above — a real-world giant package like texlive-full stays parseable), 100 000
 * entries, 10 MiB per metadata member. All three are injectable through the readers'
 * package-private overloads so boundary tests can use small values.
 */
public final class BudgetLimits {

    public static final long DEFAULT_DECOMPRESSED_CAP = 2L * 1024 * 1024 * 1024;
    public static final int DEFAULT_MAX_ENTRIES = 100_000;
    public static final long DEFAULT_MEMBER_CAP = 10L * 1024 * 1024;

    public static final BudgetLimits DEFAULT =
            new BudgetLimits(DEFAULT_DECOMPRESSED_CAP, DEFAULT_MAX_ENTRIES, DEFAULT_MEMBER_CAP);

    private final long decompressedCap;
    private final int maxEntries;
    private final long memberCap;

    public BudgetLimits(long decompressedCap, int maxEntries, long memberCap) {
        if (decompressedCap <= 0 || maxEntries <= 0 || memberCap <= 0) {
            throw new IllegalArgumentException("budgets must be positive");
        }
        this.decompressedCap = decompressedCap;
        this.maxEntries = maxEntries;
        this.memberCap = memberCap;
    }

    public long decompressedCap() {
        return decompressedCap;
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long memberCap() {
        return memberCap;
    }

    @Override
    public @NotNull String toString() {
        return "BudgetLimits{decompressedCap=" + decompressedCap
                + ", maxEntries=" + maxEntries + ", memberCap=" + memberCap + "}";
    }
}
