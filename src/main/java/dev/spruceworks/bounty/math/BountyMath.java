package dev.spruceworks.bounty.math;

import dev.spruceworks.bounty.model.Contribution;
import java.util.Collection;

/**
 * Pure money math shared by placement, cancellation, and payout. No Bukkit
 * or storage dependency, so it is directly unit-testable.
 */
public final class BountyMath {

    private BountyMath() {
    }

    /** Amount added to the pot after burning placement-tax-% of the withdrawn amount. */
    public static double potContribution(double withdrawnAmount, double taxPercent) {
        return round2(withdrawnAmount - (withdrawnAmount * taxPercent / 100.0));
    }

    /** Amount refunded to a canceling contributor. */
    public static double refundAmount(double contributionAmount, double refundPercent) {
        return round2(contributionAmount * refundPercent / 100.0);
    }

    /** Sum of all contributions on a bounty — the payout on claim. */
    public static double stackedTotal(Collection<Contribution> contributions) {
        return round2(contributions.stream().mapToDouble(Contribution::amount).sum());
    }

    public static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
