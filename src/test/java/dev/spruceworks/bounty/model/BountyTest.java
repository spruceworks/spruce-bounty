package dev.spruceworks.bounty.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BountyTest {

    @Test
    void sameContributorAddingTwiceAccumulatesIntoOneContribution() {
        UUID target = UUID.randomUUID();
        UUID placer = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-07-24T12:00:00Z");
        Instant t2 = t1.plusSeconds(60);
        Bounty bounty = new Bounty(target, t1, t1);

        bounty.addContribution(placer, 40.0, t1);
        bounty.addContribution(placer, 10.0, t2);

        assertEquals(1, bounty.contributorCount());
        assertEquals(50.0, bounty.contributionOf(placer).amount());
        assertEquals(50.0, bounty.total());
        assertEquals(t2, bounty.lastUpdatedAt());
    }

    @Test
    void differentContributorsStackIndependently() {
        UUID target = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = Instant.now();
        Bounty bounty = new Bounty(target, now, now);

        bounty.addContribution(first, 30.0, now);
        bounty.addContribution(second, 20.0, now);

        assertEquals(2, bounty.contributorCount());
        assertEquals(50.0, bounty.total());
    }

    @Test
    void removingTheOnlyContributionEmptiesTheBounty() {
        UUID target = UUID.randomUUID();
        UUID placer = UUID.randomUUID();
        Instant now = Instant.now();
        Bounty bounty = new Bounty(target, now, now);
        bounty.addContribution(placer, 10.0, now);

        bounty.removeContribution(placer, now);

        assertTrue(bounty.isEmpty());
        assertNull(bounty.contributionOf(placer));
    }
}
