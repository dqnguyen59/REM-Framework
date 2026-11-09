package remframework.opengl.model;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.entity.Node;

/** Saved measurements and reconstructed sampling stencils for one radial receiver line. */
public final class SurveyLineInspection {
    private SurveyLineInspection() { }
    public record Point(double update,double value) { }
    public record Measurement(int shell,double radius,double arrival,double peak,String status,List<Point> trace) { }
    public record Selection(String location,int orientation,int ray,Vector3d source,
                            List<ProbeDefinition> probes,List<Measurement> measurements) { }

    /** Cached breadth-first distances through the current movable lattice; no mechanics is changed. */
    public static final class PathFinder {
        private final remframework.lattice.DiamondTopology topology;
        private final DiamondProbePaths paths;
        private int cachedTarget=-1;
        private Vector3d cachedLineEnd;
        private List<Vector3i> cachedRoute=List.of();
        public PathFinder(Object[] nodes,Vector3d origin) {
            var dimensions=new Vector3i();for(Object value:nodes)dimensions.max(((Node)value).getIndex());dimensions.add(1,1,1);
            topology=new remframework.lattice.DiamondTopology(dimensions.x,dimensions.y,dimensions.z);
            var blocked=new boolean[topology.size()];int source=-1;
            for(Object value:nodes) {
                var node=(Node)value;int id=topology.idAtIndex(node.getIndex());blocked[id]=node.isFixed()||node.isWall();
                if(node.getEquilibriumPosition().distanceSquared(origin)<1e-16)source=id;
            }
            paths=new DiamondProbePaths(topology,source,id->!blocked[id]);
        }
        public List<Vector3i> pathTo(Vector3i target) {
            return paths.pathTo(topology.idAtIndex(target)).stream().map(id->topology.index(id,new Vector3i())).toList();
        }
        public List<Vector3i> pathTo(Vector3i target,Vector3d probePosition) {
            int id=topology.idAtIndex(target);
            if(id!=cachedTarget||cachedLineEnd==null||!probePosition.equals(cachedLineEnd)) {
                cachedRoute=paths.pathTo(id,probePosition).stream().map(node->topology.index(node,new Vector3i())).toList();
                cachedTarget=id;cachedLineEnd=new Vector3d(probePosition);
            }
            return cachedRoute.stream().map(Vector3i::new).toList();
        }
    }

    /** Reads only the selected line; sample update coordinates are retained without rounding. */
    public static List<Measurement> readMeasurements(Path directory,int orientation,int ray) throws IOException {
        var traces=new HashMap<Integer,List<Point>>();
        Path file=directory.resolve("traces.csv");
        if(Files.exists(file))try(var reader=Files.newBufferedReader(file)) {
            reader.readLine();String line;
            while((line=reader.readLine())!=null) {
                var c=line.split(",",-1);
                if(Integer.parseInt(c[0])==orientation&&Integer.parseInt(c[2])==ray)
                    traces.computeIfAbsent(Integer.parseInt(c[1]),s->new ArrayList<>())
                            .add(new Point(Double.parseDouble(c[3]),Double.parseDouble(c[4])));
            }
        }
        var result=new ArrayList<Measurement>();
        try(var reader=Files.newBufferedReader(directory.resolve("probes.csv"))) {
            var names=List.of(reader.readLine().split(",",-1));String line;
            while((line=reader.readLine())!=null) {
                var c=line.split(",",-1);
                if(Integer.parseInt(c[names.indexOf("orientation")])!=orientation||Integer.parseInt(c[names.indexOf("ray")])!=ray)continue;
                int shell=Integer.parseInt(c[names.indexOf("shell")]);
                var trace=traces.getOrDefault(shell,List.of()).stream().sorted(Comparator.comparingDouble(Point::update)).toList();
                result.add(new Measurement(shell,Double.parseDouble(c[names.indexOf("radius")]),
                        Double.parseDouble(c[names.indexOf("arrival_half_peak")]),Double.parseDouble(c[names.indexOf("peak")]),
                        c[names.indexOf("status")],trace));
            }
        }
        result.sort(Comparator.comparingInt(Measurement::shell));return List.copyOf(result);
    }

    /** Geometry is reconstructed at equilibrium; historical individual-node motion was not recorded. */
    public static Selection load(TransverseIdentitySurvey.ViewState view,int orientation,int ray,Object[] nodes) throws IOException {
        var properties=new Properties();Path directory=Path.of(view.location());
        try(var reader=Files.newBufferedReader(directory.resolve("configuration.properties"))){properties.load(reader);}
        var dimensions=new Vector3i();for(Object value:nodes)dimensions.max(((Node)value).getIndex());dimensions.add(1,1,1);
        if(dimensions.x!=view.dimensionX()||dimensions.y!=view.dimensionY()||dimensions.z!=view.dimensionZ())
            throw new IOException("Load the saved lattice dimensions before highlighting this run");
        Node source=null;
        if(properties.containsKey("sourceIndexX")) {
            var index=new Vector3i(Integer.parseInt(properties.getProperty("sourceIndexX")),Integer.parseInt(properties.getProperty("sourceIndexY")),Integer.parseInt(properties.getProperty("sourceIndexZ")));
            for(Object value:nodes)if(((Node)value).getIndex().equals(index)){source=(Node)value;break;}
        } else {
            var matcher=java.util.regex.Pattern.compile("[-+]?\\d+(?:\\.\\d*)?(?:[Ee][-+]?\\d+)?").matcher(properties.getProperty("sourcePosition",""));
            var coordinates=new ArrayList<Double>();while(matcher.find())coordinates.add(Double.parseDouble(matcher.group()));
            if(coordinates.size()!=3)throw new IOException("Saved source coordinates are unavailable");
            var position=new Vector3d(coordinates.get(0),coordinates.get(1),coordinates.get(2));
            for(Object value:nodes)if(((Node)value).getEquilibriumPosition().distance(position)<1e-3) {
                if(source!=null)throw new IOException("Saved source coordinate is ambiguous");source=(Node)value;
            }
        }
        if(source==null)throw new IOException("Saved source node is absent from this lattice");
        var distance=view.receiverDistances().stream().filter(r->r.orientation()==orientation&&r.ray()==ray).findFirst()
                .orElseThrow(()->new IOException("No saved geometry for this receiver line"));
        var direction=new Vector3d(distance.nx(),distance.ny(),distance.nz());
        var probes=DiamondProbePlacement.create(nodes,Integer.parseInt(properties.getProperty("firstRadius")),
                Integer.parseInt(properties.getProperty("spacing")),view.shells(),false,List.of(direction),source.getEquilibriumPosition());
        return new Selection(view.location(),orientation,ray,new Vector3d(source.getEquilibriumPosition()),probes,readMeasurements(directory,orientation,ray));
    }
}
