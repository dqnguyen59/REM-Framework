package remframework.gui.view.frames.dialogs;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.gui.view.frames.AbstractFrame;
import remframework.opengl.model.DiamondProbePlacement;
import remframework.opengl.model.ProbeDefinition;
import remframework.settings.Project;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Diamond-only physical shell builder. Geometry calculations never run in layout(). */
public final class FrameAddDiamondProbes extends AbstractFrame {
    private final Project project=Project.getInstance();
    private int first=16,spacing=4,shells=2;
    private final StringBuffer firstBuffer=new StringBuffer(),spacingBuffer=new StringBuffer(),shellBuffer=new StringBuffer();
    private record Preview(List<ProbeDefinition> definitions,String error) { }
    private CompletableFuture<Preview> task;
    private Object[] sampledNodes;
    private int sampledFirst,sampledSpacing,sampledShells,sampledDirections;
    private final StringBuffer directionsBuffer=new StringBuffer();
    private boolean xyOnly=true,sampledXY;
    private String status="Generate equal-radius shells in 26 physical directions.";
    public FrameAddDiamondProbes(NuklearModel model) {
        super(model);windowOptions &= ~Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }
    @Override public String getTitle() {return "Diamond Probes";}
    @Override public void render(long window,NkContext ctx) {
        super.render(window,ctx);createLayoutCentered(ctx,620,600);
    }
    @Override protected void layout(NkContext ctx,int x,int y,int width,int height) {
        var lattice=project.scene.getLattice();
        if(!lattice.isMechanicsOnly()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeight*2,1);
            Nuklear.nk_label_wrap(ctx,"Select the diamond lattice before creating diamond probes.");return;
        }
        xyOnly=nk_check_label(ctx,"XY plane only",xyOnly,.8,.2);
        try(MemoryStack stack=MemoryStack.stackPush()) {
            if(xyOnly) {
                int count=nk_label_edit(ctx,stack,"XY directions (8, 16, 32, 64)",directionsBuffer,appSettings.getDiamondProbeXYDirections(),.65,.35);
                if(DiamondProbePlacement.validXYDirectionCount(count))appSettings.setDiamondProbeXYDirections(count);
            }
            first=nk_label_edit(ctx,stack,"First radius (bond lengths)",firstBuffer,first,.65,.35);
            spacing=nk_label_edit(ctx,stack,"Shell spacing",spacingBuffer,spacing,.65,.35);
            shells=nk_label_edit(ctx,stack,"Shell count",shellBuffer,shells,.65,.35);
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeight*3,1);
        Nuklear.nk_label_wrap(ctx,(xyOnly?appSettings.getDiamondProbeXYDirections()+" equally spaced XY directions. Fixed surface samples are included for thin slabs. ":"26 XYZ directions; fixed stencils rejected. ")+"Passive interpolation uses both diamond sublattices. Cubic probes are kept separately.");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeight,1);
        if(nk_button_label(ctx,"Generate diamond shells") && (task==null || task.isDone())) {
            if(!project.getRemModel().isLoadingReady() || lattice.getNodeArray()==null)status="Wait until lattice loading completes.";
            else {
                project.getRemModel().stopSimulation();
                sampledNodes=lattice.getNodeArray();sampledFirst=first;sampledSpacing=spacing;sampledShells=shells;
                Object[] nodes=sampledNodes;int f=first,g=spacing,n=shells;sampledXY=xyOnly;boolean planar=xyOnly;
                sampledDirections=appSettings.getDiamondProbeXYDirections();int directionCount=sampledDirections;
                status="Building interpolation stencils in the background...";
                task=CompletableFuture.supplyAsync(()-> {
                    try {return new Preview(DiamondProbePlacement.create(nodes,f,g,n,planar,directionCount),null);}
                    catch(RuntimeException error) {return new Preview(List.of(),error.getMessage());}
                });
            }
        }
        Preview preview=task!=null && task.isDone() && !task.isCancelled()
            ? task.isCompletedExceptionally()?new Preview(List.of(),"Shell generation failed; reduce dimensions or free memory."):task.getNow(null):null;
        boolean current=sampledNodes==lattice.getNodeArray() && sampledFirst==first && sampledSpacing==spacing && sampledShells==shells && sampledXY==xyOnly
            && (!xyOnly || sampledDirections==appSettings.getDiamondProbeXYDirections());
        Nuklear.nk_layout_row_dynamic(ctx,rowHeight*3,1);
        Nuklear.nk_label_wrap(ctx,preview==null?status:!current?"Lattice or shell settings changed; regenerate.":preview.error()!=null?preview.error():"Ready: "+preview.definitions().size()+" passive diamond probes. Apply replaces diamond shells only.");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeight,1);
        if(nk_button_label(ctx,"Apply diamond probes") && preview!=null && preview.error()==null && current) {
            if(lattice.isDiamondTransportEnabled())status="Disable Identity transport before applying measurement probes.";
            else {project.scene.requestDiamondProbes(sampledNodes,preview.definitions());status="Queued for completed frame";}
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeight*2,1);
        Nuklear.nk_label_wrap(ctx,status+"  "+project.scene.getDiamondProbeStatus());
        Nuklear.nk_layout_row_dynamic(ctx,rowHeight,1);
        if(nk_button_label(ctx,"Open Isotropy"))nuklearModel.showFrame(NuklearModel.Frame.isotropy);
        if(nk_button_label(ctx,"Close"))close();
    }
    @Override public void close() {if(task!=null)task.cancel(true);sampledNodes=null;super.close();}
}
