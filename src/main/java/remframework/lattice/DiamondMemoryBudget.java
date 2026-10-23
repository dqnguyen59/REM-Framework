package remframework.lattice;

import java.lang.management.ManagementFactory;
import org.joml.Vector3i;
import remframework.geometry.discovery.*;

/** Conservative peak-build estimate, including graph temporaries and runtime/render objects. */
public final class DiamondMemoryBudget {
    // Measured optimized Node runtime: ~1060 live bytes/node, ~1620 build-peak
    // bytes/node at 64^3. Retain margin for visible matrices, direct buffers and probes.
    public static final long ESTIMATED_BYTES_PER_NODE = 3072;
    private DiamondMemoryBudget() { }
    public record Estimate(long nodes, long requiredBytes, long budgetBytes, String error,
                           GeometryConfigurationStatusCode code) {
        public boolean allowed() {return error == null;}
    }
    public record MemoryAvailability(long heapMaximumBytes,long heapUsedBytes,long freeSystemBytes,
                                     long reusableCommittedBytes) {
        public MemoryAvailability(long maximum,long used,long freeSystem) {
            this(maximum,used,freeSystem,0);
        }
        public long heapHeadroomBytes() {return Math.max(0,heapMaximumBytes-heapUsedBytes);}
        public long budgetBytes() {
            // Reclaimed heap may remain committed to Java instead of returning to the OS.
            // Cap the combined capacity at heap headroom; retain the 25% reserve.
            long reusable=Math.min(heapHeadroomBytes(),Math.max(0,reusableCommittedBytes));
            long available=freeSystemBytes>0 ? reusable+Math.min(heapHeadroomBytes()-reusable,freeSystemBytes) : heapHeadroomBytes();
            return available/4*3;
        }
        public String limitingResource() {
            return freeSystemBytes>0 && freeSystemBytes<Math.max(0,heapHeadroomBytes()-reusableCommittedBytes) ? "free system RAM" : "Java heap headroom";
        }
    }
    public static MemoryAvailability memoryAvailability() {
        var runtime=Runtime.getRuntime();
        long systemFree=-1;
        if(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
            systemFree=os.getFreeMemorySize();
        return new MemoryAvailability(runtime.maxMemory(),runtime.totalMemory()-runtime.freeMemory(),systemFree,runtime.freeMemory());
    }
    public static long availableBudget() {return memoryAvailability().budgetBytes();}
    public static Estimate estimate(Vector3i size) {return estimate(size, availableBudget());}
    public static Estimate estimate(Vector3i size, long budgetBytes) {
        if (!ExperimentalDiamondLatticeModel.validDimensions(size))
            return new Estimate(0, 0, budgetBytes, "Diamond dimensions must be even values of at least 2.", GeometryConfigurationStatusCode.INVALID_PARAMETERS);
        long nodes;
        try {nodes = Math.multiplyExact(Math.multiplyExact((long)size.x, size.y), size.z);}
        catch (ArithmeticException overflow) {nodes = Long.MAX_VALUE;}
        if (nodes > Integer.MAX_VALUE - 8)
            return new Estimate(nodes, Long.MAX_VALUE, budgetBytes, "Requested lattice exceeds Java array/index capacity.", GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED);
        long required = nodes * ESTIMATED_BYTES_PER_NODE;
        if (required > budgetBytes)
            return new Estimate(nodes, required, budgetBytes,
                String.format("Conservative build estimate: %.0f MiB.\nAvailable safe build budget: %.0f MiB.\nThis estimate is not current memory usage.\nThe budget includes reusable Java heap and currently free system RAM.\nReduce dimensions or free memory before retrying.", required / 1048576.0, budgetBytes / 1048576.0), GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED);
        return new Estimate(nodes, required, budgetBytes, null, null);
    }
}
