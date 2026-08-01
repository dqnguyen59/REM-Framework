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
import org.joml.Vector3d;
import java.util.*;
public final class GeometryMeasureCalculator {
    private static final double TOLERANCE=1e-9;
    public AreaResult area(Realization r,ClosedBoundaryResult b){
        if(!b.closed()){return AreaResult.unavailable(b.status());}
        if(r.dimension()==GeometryEmbeddingDimension.TWO_D){List<Integer> ids=b.orientedFaces().get(0).nodeIds();double signed=0;for(int i=0;i<ids.size();i++){Vector3d p=r.finalCoordinates().get(ids.get(i)),q=r.finalCoordinates().get(ids.get((i+1)%ids.size()));signed+=p.x*q.y-q.x*p.y;}signed/=2;var d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.AREA_CALCULATION,GeometryConfigurationStatusCode.AREA_AVAILABLE,"Area available","Signed polygon shoelace area was calculated only after closed-boundary validation.");return new AreaResult(true,signed,Math.abs(signed),signed>=0?"COUNTERCLOCKWISE":"CLOCKWISE",Map.of("boundary",Math.abs(signed)),TOLERANCE,GeometryConfigurationStatus.of(d));}
        Map<String,Double> perFace=new LinkedHashMap<>();double total=0;for(DerivedFace face:b.orientedFaces()){List<Integer> ids=face.nodeIds();Vector3d origin=r.finalCoordinates().get(ids.get(0));double area=0;for(int i=1;i+1<ids.size();i++)area+=new Vector3d(r.finalCoordinates().get(ids.get(i))).sub(origin).cross(new Vector3d(r.finalCoordinates().get(ids.get(i+1))).sub(origin)).length()/2;perFace.put(face.id(),area);total+=area;}var d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.AREA_CALCULATION,GeometryConfigurationStatusCode.AREA_AVAILABLE,"Surface area available","Each validated planar face was triangulated deterministically as a fan.");return new AreaResult(true,total,total,"OUTWARD_UNDETERMINED",perFace,TOLERANCE,GeometryConfigurationStatus.of(d));
    }
    public VolumeResult volume(Realization r,ClosedBoundaryResult b){
        if(r.dimension()!=GeometryEmbeddingDimension.THREE_D||!b.closed()||!b.orientable())return VolumeResult.unavailable(b.status());
        Vector3d centroid=new Vector3d();r.finalCoordinates().forEach(centroid::add);centroid.div(r.finalCoordinates().size());Map<String,Double> contributions=new LinkedHashMap<>();double total=0;for(DerivedFace face:b.orientedFaces()){List<Integer> ids=new ArrayList<>(face.nodeIds());Vector3d a=r.finalCoordinates().get(ids.get(0)),bb=r.finalCoordinates().get(ids.get(1)),c=r.finalCoordinates().get(ids.get(2));Vector3d normal=new Vector3d(bb).sub(a).cross(new Vector3d(c).sub(a));Vector3d faceCenter=new Vector3d();ids.forEach(id->faceCenter.add(r.finalCoordinates().get(id)));faceCenter.div(ids.size());if(normal.dot(new Vector3d(faceCenter).sub(centroid))<0)Collections.reverse(ids);double sum=0;Vector3d origin=r.finalCoordinates().get(ids.get(0));for(int i=1;i+1<ids.size();i++)sum+=origin.dot(new Vector3d(r.finalCoordinates().get(ids.get(i))).cross(r.finalCoordinates().get(ids.get(i+1))))/6;contributions.put(face.id(),sum);total+=sum;}var d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.VOLUME_CALCULATION,GeometryConfigurationStatusCode.VOLUME_AVAILABLE,"Volume available","Signed tetrahedral contributions of the consistently outward-oriented validated surface were summed.");return new VolumeResult(true,total,Math.abs(total),total>=0?"OUTWARD":"INWARD",TOLERANCE,contributions,GeometryConfigurationStatus.of(d));
    }
}
