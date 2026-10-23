package remframework.lattice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.joml.Vector3i;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;
import java.lang.management.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in size-controlled benchmark; run in an isolated JVM for comparable heap figures. */
@EnabledIfSystemProperty(named="runMemoryAudit",matches="true")
class DiamondMemoryAuditTest {
    @Test void audit() throws Exception {
        var project=Project.getInstance();var lattice=Lattice.getInstance();var manager=LatticeManager.getInstance();manager.initialize();
        int x=Integer.getInteger("auditX",64),y=Integer.getInteger("auditY",64),z=Integer.getInteger("auditZ",64);
        manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(x,y,z,false);lattice.clearEntities();
        collect();long before=used();for(var pool:ManagementFactory.getMemoryPoolMXBeans())pool.resetPeakUsage();
        long start=System.nanoTime();lattice.buildHeadless();long end=System.nanoTime()+180_000_000_000L;
        while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
        lattice.update();assertNotNull(lattice.getNodeArray(),"Build rejected by memory guard");assertEquals((long)x*y*z,lattice.getNodeArray().length);
        long buildNanos=System.nanoTime()-start;collect();long live=used()-before;
        long peak=ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==MemoryType.HEAP).mapToLong(p->p.getPeakUsage().getUsed()).sum();
        System.out.println("MEMORY_AUDIT_READY pid="+ProcessHandle.current().pid()+" nodes="+lattice.getNodeArray().length);
        String histogram=System.getProperty("auditHistogram");
        if(histogram!=null)assertEquals(0,new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","jcmd.exe").toString(),
            Long.toString(ProcessHandle.current().pid()),"GC.class_histogram").redirectErrorStream(true).redirectOutput(Path.of(histogram).toFile()).start().waitFor());
        int hold=Integer.getInteger("auditHoldSeconds",0);if(hold>0)Thread.sleep(hold*1000L);
        project.getRemModel().startSimulation();start=System.nanoTime();
        for(int step=0;step<5;step++) {project.scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));project.scene.waitForAllTasksReady();for(Object value:lattice.getNodeArray())((Node)value).updatePosition();}
        long updateNanos=System.nanoTime()-start;collect();long afterUpdates=used()-before;
        String csv="x,y,z,nodes,live_bytes,bytes_per_node,heap_peak_envelope_bytes,after_updates_bytes,build_ms,five_updates_ms\n"+
            x+","+y+","+z+","+((long)x*y*z)+","+live+","+(live/(double)((long)x*y*z))+","+peak+","+afterUpdates+","+(buildNanos/1e6)+","+(updateNanos/1e6)+"\n";
        System.out.println(csv);Files.writeString(Path.of(System.getProperty("auditOutput","diamond-memory-audit.csv")),csv);
        project.getRemModel().stopSimulation();
    }
    private static long used(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
    private static void collect() throws InterruptedException {for(int i=0;i<3;i++){System.gc();Thread.sleep(100);}}
}
