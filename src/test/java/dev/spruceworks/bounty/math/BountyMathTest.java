package dev.spruceworks.bounty.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.spruceworks.bounty.model.Contribution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BountyMathTest {

    @Test
    void potContributionWithNoTaxKeepsFullAmount() {
        assertEquals(100.0, BountyMath.potContribution(100.0, 0.0));
    }

    @Test
    void potContributionBurnsConfiguredTaxPercent() {
        assertEquals(90.0, BountyMath.potContribution(100.0, 10.0));
    }

    @Test
    void potContributionRoundsToTwoDecimals() {
        // 33.33% tax on 10.0 burns 3.333 -> pot gets 6.667 -> rounds to 6.67.
        assertEquals(6.67, BountyMath.potContribution(10.0, 33.33));
    }

    @Test
    void refundAmountAppliesConfiguredPercent() {
        assertEquals(75.0, BountyMath.refundAmount(100.0, 75.0));
    }

    @Test
    void refundAmountOfZeroPercentIsZero() {
        assertEquals(0.0, BountyMath.refundAmount(100.0, 0.0));
    }

    @Test
    void refundAmountOfFullPercentReturnsWholeContribution() {
        assertEquals(42.5, BountyMath.refundAmount(42.5, 100.0));
    }

    @Test
    void stackedTotalSumsAllContributions() {
        List<Contribution> contributions = List.of(
                new Contribution(UUID.randomUUID(), 50.0, Instant.now()),
                new Contribution(UUID.randomUUID(), 25.5, Instant.now()),
                new Contribution(UUID.randomUUID(), 24.5, Instant.now()));
        assertEquals(100.0, BountyMath.stackedTotal(contributions));
    }

    @Test
    void stackedTotalOfEmptyCollectionIsZero() {
        assertEquals(0.0, BountyMath.stackedTotal(List.of()));
    }

    @Test
    void round2RoundsToNearestCent() {
        assertEquals(1.23, BountyMath.round2(1.2299));
        assertEquals(1.24, BountyMath.round2(1.2351));
    }
}
