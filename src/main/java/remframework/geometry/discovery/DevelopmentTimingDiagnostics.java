package remframework.geometry.discovery;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Optional development-only aggregate timings enabled by {@code -Drem.geometry.timing=true}. */
public final class DevelopmentTimingDiagnostics {
    private static final boolean ENABLED=Boolean.getBoolean("rem.geometry.timing");
    private static final ConcurrentHashMap<String,Sample> SAMPLES=new ConcurrentHashMap<>();
    private DevelopmentTimingDiagnostics() { }
    public static <T> T measure(String name,Supplier<T> work){if(!ENABLED)return work.get();long started=System.nanoTime();try{return work.get();}finally{record(name,System.nanoTime()-started);}}
    public static void measure(String name,Runnable work){measure(name,()->{work.run();return null;});}
    public static void record(String name,long nanos){if(!ENABLED)return;Sample sample=SAMPLES.computeIfAbsent(name,ignored->new Sample());sample.count.increment();sample.nanos.add(nanos);}
    public static Map<String,Timing> snapshot(){Map<String,Timing> result=new TreeMap<>();SAMPLES.forEach((name,sample)->result.put(name,new Timing(sample.count.sum(),sample.nanos.sum())));return Map.copyOf(result);}
    public static void clear(){SAMPLES.clear();}
    private static final class Sample { private final LongAdder count=new LongAdder(),nanos=new LongAdder(); }
    public record Timing(long invocations,long totalNanos){public double averageMillis(){return invocations==0?0:totalNanos/1_000_000d/invocations;}}
}
