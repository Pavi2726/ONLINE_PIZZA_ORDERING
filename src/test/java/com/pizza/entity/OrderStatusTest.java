package com.pizza.entity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure enum test for {@link OrderStatus#canTransitionTo(String)} - no mocks,
 * no Spring context.
 *
 * <p>{@code expectedTransitions} mirrors the {@code allowedNextStatuses} sets
 * declared on each {@link OrderStatus} constant exactly as read from source:
 * PLACED -&gt; {PROCESSING, CANCELLED}; PROCESSING -&gt; {OUT_FOR_DELIVERY,
 * CANCELLED}; OUT_FOR_DELIVERY -&gt; {DELIVERED}; DELIVERED -&gt; {}; CANCELLED
 * -&gt; {}. Every (from, to) pair among all five constants is asserted, plus an
 * unrecognized target string.</p>
 */
class OrderStatusTest {

    private static final Map<OrderStatus, Set<String>> EXPECTED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        EXPECTED_TRANSITIONS.put(OrderStatus.PLACED, Set.of("PROCESSING", "CANCELLED"));
        EXPECTED_TRANSITIONS.put(OrderStatus.PROCESSING, Set.of("OUT_FOR_DELIVERY", "CANCELLED"));
        EXPECTED_TRANSITIONS.put(OrderStatus.OUT_FOR_DELIVERY, Set.of("DELIVERED"));
        EXPECTED_TRANSITIONS.put(OrderStatus.DELIVERED, Set.of());
        EXPECTED_TRANSITIONS.put(OrderStatus.CANCELLED, Set.of());
    }

    /** Every (from, to) pair among all {@link OrderStatus} constants. */
    static Stream<Arguments> allPairs() {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expected = EXPECTED_TRANSITIONS.get(from).contains(to.name());
                builder.add(Arguments.of(from, to, expected));
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0} -> {1} should be {2}")
    @MethodSource("allPairs")
    void canTransitionTo_matchesAllowedNextStatusesForEveryPair(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to.name())).isEqualTo(expected);
    }

    @Test
    void canTransitionTo_unrecognizedTargetStatus_returnsFalseForEveryConstant() {
        for (OrderStatus from : OrderStatus.values()) {
            assertThat(from.canTransitionTo("NOT_A_REAL_STATUS")).isFalse();
        }
    }

    /**
     * {@code allowedNextStatuses} is built from {@link Set#of}, whose
     * {@code contains(null)} throws {@link NullPointerException} rather than
     * returning {@code false} (verified against the JDK's immutable-set
     * behaviour) - so a null target status propagates as an NPE, not a quiet
     * {@code false}.
     */
    @Test
    void canTransitionTo_nullTarget_throwsNullPointerException() {
        for (OrderStatus from : OrderStatus.values()) {
            assertThrows(NullPointerException.class, () -> from.canTransitionTo(null));
        }
    }
}
