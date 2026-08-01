package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
/** Enforces the research-stage ordering, especially the boundary gate. */
public final class GeometryConfigurationStatusTransitions {
    private static final EnumMap<GeometryConfigurationStage,Set<GeometryConfigurationStage>> NEXT=new EnumMap<>(GeometryConfigurationStage.class);
    static {GeometryConfigurationStage[] stages=GeometryConfigurationStage.values();for(int i=0;i<stages.length;i++)NEXT.put(stages[i],i+1<stages.length?EnumSet.of(stages[i],stages[i+1]):EnumSet.of(stages[i]));}
    public boolean valid(GeometryConfigurationStatus from,GeometryConfigurationStatus to){if(from==null)return to.stage()==GeometryConfigurationStage.REQUEST_VALIDATION;return NEXT.get(from.stage()).contains(to.stage());}
    public void requireValid(GeometryConfigurationStatus from,GeometryConfigurationStatus to){if(!valid(from,to))throw new IllegalStateException("Invalid Geometry Discovery status transition from "+(from==null?"START":from.stage())+" to "+to.stage()+".");}
    public void requireMeasureEligible(ClosedBoundaryResult boundary,GeometryConfigurationStage measureStage){if(measureStage!=GeometryConfigurationStage.AREA_CALCULATION&&measureStage!=GeometryConfigurationStage.VOLUME_CALCULATION)throw new IllegalArgumentException("Expected an area or volume stage.");if(boundary==null||!boundary.closed()||!boundary.orientable())throw new IllegalStateException("Area/volume stages require a validated unique closed boundary.");}
}
