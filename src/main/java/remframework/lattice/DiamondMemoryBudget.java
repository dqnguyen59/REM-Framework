package remframework.lattice;

import java.lang.management.ManagementFactory;
import org.joml.Vector3i;
import remframework.geometry.discovery.*;

/** Conservative peak-build estimate, including graph temporaries and runtime/render objects. */
public final class DiamondMemoryBudget {
    public static final long ESTIMATED_BYTES_PER_NODE = 8192;
    private DiamondMemoryBudget() { }
    public record Estimate(long nodes, long requiredBytes, long budgetBytes, String error,
                           GeometryConfigurationStatusCode code) {
        public boolean allowed() {return error == null;}
    }
    public record MemoryAvailability(long heapMaximumBytes,long heapUsedBytes,long freeSystemBytes) {
        public long heapHeadroomBytes() {return Math.max(0,heapMaximumBytes-heapUsedBytes);}
        public long budgetBytes() {
            long available=freeSystemBytes>0 ? Math.min(heapHeadroomBytes(),freeSystemBytes) : heapHeadroomBytes();
            return available/4*3;
        }
        public String limitingResource() {
            return freeSystemBytes>0 && freeSystemBytes<heapHeadroomBytes() ? "free system RAM" : "Java heap headroom";
        }
    }
    public static MemoryAvailability memoryAvailability() {
        var runtime=Runtime.getRuntime();
        long systemFree=-1;
        if(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
            systemFree=os.getFreeMemorySize();
        return new MemoryAvailability(runtime.maxMemory(),runtime.totalMemory()-runtime.freeMemory(),systemFree);
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
                String.format("Estimated peak %.0f MiB exceeds available budget %.0f MiB. Reduce dimensions or increase the JVM heap (-Xmx).", required / 1048576.0, budgetBytes / 1048576.0), GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED);
        return new Estimate(nodes, required, budgetBytes, null, null);
    }
}
