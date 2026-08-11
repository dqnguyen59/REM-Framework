package remframework.measurement.compact;

import org.joml.Vector3d;

/** Measurement-only mirror of the receiver state used by Occupancy/TransportSystem. */
public final class CompactTransportReceiver {
    public static final int HISTORY_LENGTH = 100;
    private final Vector3d[] history = new Vector3d[HISTORY_LENGTH];
    private final Vector3d maxPositive = new Vector3d();
    private final Vector3d maxNegative = new Vector3d();
    private final Vector3d lastMaxPositive = new Vector3d();
    private final Vector3d lastMaxNegative = new Vector3d();
    private final Vector3d gradientImbalance = new Vector3d();
    private final Vector3d acceleration = new Vector3d();
    private final Vector3d transportMomentum = new Vector3d();
    private final Vector3d pressure = new Vector3d();
    private int historyIndex;
    private int cycleSampleCount;
    private int samplesPerCycle = HISTORY_LENGTH;
    private boolean signIsPositiveZ = true;
    private int transportCyclesX, transportCyclesY, transportCyclesZ;

    public CompactTransportReceiver() {
        for (int i = 0; i < history.length; i++) history[i] = new Vector3d();
        resetHistory();
    }

    public Snapshot update(Vector3d rawGradient, double receiverVelocityZ,
                           double amplitudeLength, double gradientGain,
                           double accelerationGain, double momentumGain,
                           double momentumMax) {
        Vector3d scaled = new Vector3d(rawGradient).mul(gradientGain);
        saveGradientToHistory(scaled);
        boolean cycleCompleted = isCycleCompleted(receiverVelocityZ);
        acceleration.set(calculate(scaled.x, amplitudeLength, accelerationGain),
                calculate(scaled.y, amplitudeLength, accelerationGain),
                calculate(scaled.z, amplitudeLength, accelerationGain));
        Vector3d momentumBefore = new Vector3d(transportMomentum);
        transportMomentum.fma(momentumGain, acceleration);
        transportMomentum.set(Math.clamp(transportMomentum.x, -momentumMax, momentumMax),
                Math.clamp(transportMomentum.y, -momentumMax, momentumMax),
                Math.clamp(transportMomentum.z, -momentumMax, momentumMax));
        Vector3d pressureBefore = new Vector3d(pressure);
        pressure.add(transportMomentum);
        return new Snapshot(new Vector3d(rawGradient), scaled, cycleCompleted,
                new Vector3d(lastMaxPositive), new Vector3d(lastMaxNegative),
                new Vector3d(gradientImbalance), new Vector3d(acceleration),
                momentumBefore, new Vector3d(transportMomentum),
                pressureBefore, new Vector3d(pressure), samplesPerCycle,
                cycleSampleCount, signIsPositiveZ);
    }

    private static double calculate(double gradient, double amplitude, double gain) {
        if (amplitude == 0) return 0;
        double squared = gradient * gradient;
        double value = gain * squared / amplitude;
        return gradient < 0 ? value * -1.0 : value;
    }

    private void saveGradientToHistory(Vector3d gradient) {
        history[historyIndex].set(gradient);
        historyIndex = (historyIndex + 1) % HISTORY_LENGTH;
        cycleSampleCount++;
    }

    private boolean isCycleCompleted(double velocityZ) {
        if (signIsPositiveZ && velocityZ < 0) {
            signIsPositiveZ = false;
        } else if (!signIsPositiveZ && velocityZ >= 0) {
            updateMaxGradient();
            signIsPositiveZ = true;
            samplesPerCycle = cycleSampleCount;
            historyIndex = 0;
            cycleSampleCount = 0;
            resetMaxGradient();
            resetHistory();
            return true;
        }
        return false;
    }

    private void updateMaxGradient() {
        maxPositive.zero(); maxNegative.zero();
        for (Vector3d value : history) { maxPositive.max(value); maxNegative.min(value); }
        gradientImbalance.set(maxPositive).add(maxNegative).mul(0.5);
        lastMaxPositive.set(maxPositive); lastMaxNegative.set(maxNegative);
    }

    private void resetMaxGradient() { maxPositive.zero(); maxNegative.zero(); }
    private void resetHistory() { for (Vector3d value : history) value.zero(); }

    public record Snapshot(Vector3d rawGradient, Vector3d scaledGradient,
                           boolean cycleCompleted, Vector3d maxPositiveGradient,
                           Vector3d maxNegativeGradient, Vector3d gradientImbalance,
                           Vector3d acceleration, Vector3d momentumBefore,
                           Vector3d momentumAfter, Vector3d pressureBefore,
                           Vector3d pressureAfter, int samplesPerCycle,
                           int cycleSampleCount, boolean signIsPositiveZ) { }

    public boolean isSignIsPositiveZ() { return signIsPositiveZ; }
    public int getCycleSampleCount() { return cycleSampleCount; }
    public int getSamplesPerCycle() { return samplesPerCycle; }
}
