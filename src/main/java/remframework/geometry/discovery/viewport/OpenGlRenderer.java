package remframework.geometry.discovery.viewport;
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

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenGlRenderer implements AutoCloseable {
    private final int program;
    private final int vao;
    private final int vbo;
    private final int matrixLocation;
    private final int opacityLocation;
    private GeometryDiscoveryModel cachedBoundingBoxModel;
    private List<Float> cachedBoundingBox=List.of();
    private RenderModel cachedFaceModel;
    private List<Float> cachedFaceTriangles=List.of();
    private List<Float> cachedFaceNormals=List.of();

    public OpenGlRenderer() {
        program = createProgram();
        matrixLocation = GL20.glGetUniformLocation(program, "viewProjection");
        opacityLocation=GL20.glGetUniformLocation(program,"opacity");
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 24, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 24, 12);
        GL30.glBindVertexArray(0);
    }

    public void render(GeometryDiscoveryVisualizer visualizer, int width, int height) {
        render(visualizer.session().model(), visualizer.session().settings(),
                visualizer.viewState(), width, height);
    }

    public void render(GeometryDiscoveryModel model, GeometryDiscoverySettings settings,
                       ViewState view, int width, int height) {
        ViewFit fit = view.calculateFit(model);
        Matrix4f matrix = viewProjection(view, fit, width, height);
        GL20.glUseProgram(program);
        GL20.glUniform1f(opacityLocation,1f);
        GL20.glUniformMatrix4fv(matrixLocation, false, matrix.get(new float[16]));
        GL30.glBindVertexArray(vao);

        List<Float> lines = new ArrayList<>();
        if (view.showAxes()) addAxes(lines, fit);
        addGrid(lines, fit);
        draw(lines, GL11.GL_LINES, 1.5f);
        if(view.showBoundingBox()){long boxStarted=System.nanoTime();if(cachedBoundingBoxModel!=model){List<Float> boundingBox=new ArrayList<>();addBoundingBox(boundingBox,model);cachedBoundingBox=List.copyOf(boundingBox);cachedBoundingBoxModel=model;}draw(cachedBoundingBox,GL11.GL_LINES,1f);DevelopmentTimingDiagnostics.record("viewport.bounding-box-render",System.nanoTime()-boxStarted);}
        if (view.showConnections()) {
            List<Float> tooShort = new ArrayList<>();
            List<Float> satisfied = new ArrayList<>();
            List<Float> tooLong = new ArrayList<>();
            addConnections(tooShort, satisfied, tooLong, model, settings,
                    view.edgeErrorColoring());
            // Width as well as color distinguishes the three states.
            draw(tooShort, GL11.GL_LINES, 1.5f);
            draw(tooLong, GL11.GL_LINES, 2.75f);
            draw(satisfied, GL11.GL_LINES, 4f);
        }

        List<Float> points = new ArrayList<>();
        for (DiscoveryNode node : model.nodes()) {
            if (!view.showVertices()) continue;
            boolean selected = java.util.Objects.equals(node.id(), view.selectedNodeId());
            float r = selected ? 1f : node.fixedPosition() && view.highlightFixedNodes() ? 1f : 0.2f;
            float g = selected ? 0.85f : node.fixedPosition() && view.highlightFixedNodes() ? 0.25f : 0.65f;
            float b = selected ? 0.1f : node.fixedPosition() && view.highlightFixedNodes() ? 0.15f : 1f;
            vertex(points, node.position(), r, g, b);
        }
        GL11.glPointSize(11f);
        draw(points, GL11.GL_POINTS, 1f);
        if (view.showNodeLabels()){long labelStarted=System.nanoTime();drawCanonicalLabels(model, matrix, width, height);DevelopmentTimingDiagnostics.record("viewport.label-render",System.nanoTime()-labelStarted);}
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    public void render(RenderModel renderModel,GeometryDiscoverySettings settings,ViewState view,int width,int height){
        long started=System.nanoTime();try{render(renderModel.model(),settings,view,width,height);
        if(view.showDerivedFaces())renderFaces(renderModel,view,width,height);renderInitializationTrails(renderModel,view,width,height);renderInspection(renderModel,view,width,height);}finally{DevelopmentTimingDiagnostics.record("viewport.opengl-render",System.nanoTime()-started);}
    }

    private void renderInitializationTrails(RenderModel model,ViewState view,int width,int height){if(view.initializationTrailMode()==InitializationTrailMode.OFF||model.initializationTrails().isEmpty())return;List<Float> lines=new ArrayList<>();for(List<Vector3d> path:model.initializationTrails()){int start=view.initializationTrailMode()==InitializationTrailMode.SHORT?Math.max(1,path.size()-12):1;for(int i=start;i<path.size();i++)line(lines,model.transform().display(path.get(i-1)),model.transform().display(path.get(i)),.75f,.35f,1f);}ViewFit fit=view.calculateFit(model.model());Matrix4f matrix=viewProjection(view,fit,width,height);GL20.glUseProgram(program);GL20.glUniform1f(opacityLocation,1f);GL20.glUniformMatrix4fv(matrixLocation,false,matrix.get(new float[16]));GL30.glBindVertexArray(vao);draw(lines,GL11.GL_LINES,1.5f);GL30.glBindVertexArray(0);GL20.glUseProgram(0);}

    private void renderFaces(RenderModel renderModel,ViewState view,int width,int height){
        GeometryDiscoveryModel model=renderModel.model();Map<Integer,DiscoveryNode> byId=new HashMap<>();model.nodes().forEach(n->byId.put(n.id(),n));
        ViewFit fit=view.calculateFit(model);Matrix4f matrix=viewProjection(view,fit,width,height);GL20.glUseProgram(program);GL20.glUniformMatrix4fv(matrixLocation,false,matrix.get(new float[16]));GL30.glBindVertexArray(vao);
        long constructionStarted=System.nanoTime();if(cachedFaceModel!=renderModel){List<Float> triangles=new ArrayList<>(),normals=new ArrayList<>();for(DerivedFace face:renderModel.derivedFaces()){if(face.nodeIds().size()<3)continue;Vector3d origin=byId.get(face.nodeIds().get(0)).position();for(int i=1;i+1<face.nodeIds().size();i++){vertex(triangles,origin,.12f,.55f,.8f);vertex(triangles,byId.get(face.nodeIds().get(i)).position(),.12f,.55f,.8f);vertex(triangles,byId.get(face.nodeIds().get(i+1)).position(),.12f,.55f,.8f);}Vector3d center=new Vector3d();face.nodeIds().forEach(id->center.add(byId.get(id).position()));center.div(face.nodeIds().size());Vector3d n=new Vector3d(byId.get(face.nodeIds().get(1)).position()).sub(origin).cross(new Vector3d(byId.get(face.nodeIds().get(2)).position()).sub(origin));if(n.lengthSquared()>1e-12)line(normals,center,new Vector3d(n.normalize(.35)).add(center),1,.45f,.9f);}cachedFaceTriangles=List.copyOf(triangles);cachedFaceNormals=List.copyOf(normals);cachedFaceModel=renderModel;}DevelopmentTimingDiagnostics.record("viewport.face-triangulation",System.nanoTime()-constructionStarted);
        long faceStarted=System.nanoTime();
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL20.glUniform1f(opacityLocation,.18f);GL11.glDepthMask(false);draw(cachedFaceTriangles,GL11.GL_TRIANGLES,1);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);GL20.glUniform1f(opacityLocation,1f);if(view.showFaceNormals()&&!view.showSelectedFaceNormalOnly())draw(cachedFaceNormals,GL11.GL_LINES,2);GL30.glBindVertexArray(0);GL20.glUseProgram(0);
        DevelopmentTimingDiagnostics.record("viewport.translucent-face-render",System.nanoTime()-faceStarted);
    }

    private void renderInspection(RenderModel rm,ViewState view,int width,int height){
        Map<Integer,DiscoveryNode> byId=new HashMap<>();rm.model().nodes().forEach(n->byId.put(n.id(),n));List<Float> lines=new ArrayList<>(),points=new ArrayList<>();Vector3d center=new Vector3d();
        if(view.showGeometryCenter()){vertex(points,center,1f,.15f,.85f);if(view.showCenterToVertexLines())for(DiscoveryNode n:rm.model().nodes())line(lines,center,n.position(),.55f,.2f,.7f);}
        if(view.showCenterToFaceLines())for(DerivedFace f:rm.derivedFaces()){Vector3d centroid=centroid(f,byId),normal=normal(f,byId);if(normal.lengthSquared()>0){Vector3d projection=new Vector3d(center).sub(new Vector3d(normal).mul(new Vector3d(center).sub(centroid).dot(normal)));line(lines,center,projection,.95f,.65f,.15f);}}
        SelectedElement selected=view.selectedElement();if(selected!=null&&selected.type()==SelectedElement.Type.FACE){DerivedFace f=rm.derivedFaces().stream().filter(x->x.id().equals(selected.id())).findFirst().orElse(null);if(f!=null){Vector3d centroid=centroid(f,byId),normal=normal(f,byId);for(int i=0;i<f.nodeIds().size();i++){DiscoveryNode a=byId.get(f.nodeIds().get(i)),b=byId.get(f.nodeIds().get((i+1)%f.nodeIds().size()));line(lines,a.position(),b.position(),1f,.82f,.08f);vertex(points,a.position(),1f,.82f,.08f);}if(normal.lengthSquared()>0){line(lines,centroid,new Vector3d(normal).mul(view.normalDisplayLength()).add(centroid),.1f,1f,.35f);Vector3d projection=new Vector3d(center).sub(new Vector3d(normal).mul(new Vector3d(center).sub(centroid).dot(normal)));line(lines,center,projection,1f,.55f,.1f);line(lines,center,centroid,.65f,.25f,1f);}}}
        if(selected!=null&&selected.type()==SelectedElement.Type.EDGE){String[] q=selected.id().split("-");DiscoveryNode a=byId.get(Integer.parseInt(q[1])),b=byId.get(Integer.parseInt(q[2]));if(a!=null&&b!=null)line(lines,a.position(),b.position(),1f,.82f,.08f);}
        ViewFit fit=view.calculateFit(rm.model());Matrix4f matrix=viewProjection(view,fit,width,height);GL20.glUseProgram(program);GL20.glUniform1f(opacityLocation,1f);GL20.glUniformMatrix4fv(matrixLocation,false,matrix.get(new float[16]));GL30.glBindVertexArray(vao);draw(lines,GL11.GL_LINES,3);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glPointSize(16);draw(points,GL11.GL_POINTS,1);drawInspectionLabels(rm,view,matrix,width,height,byId);GL11.glEnable(GL11.GL_DEPTH_TEST);GL30.glBindVertexArray(0);GL20.glUseProgram(0);
    }
    private void drawInspectionLabels(RenderModel rm,ViewState view,Matrix4f matrix,int width,int height,Map<Integer,DiscoveryNode> nodes){List<Float> glyphs=new ArrayList<>();float px=2f/Math.max(1,width),py=2f/Math.max(1,height);if(view.showGeometryCenter()&&view.showCenterLabel()){Vector4f q=new Vector4f(0,0,0,1).mul(matrix);if(q.w>0)addLetterC(glyphs,q.x/q.w+8*px,q.y/q.w+6*py,px,py);}if(view.showEdgeLengths()){SelectedElement selected=view.selectedElement();for(DiscoveryConnection e:rm.model().connections()){if(view.showSelectedEdgeLengthOnly()&&(selected==null||selected.type()!=SelectedElement.Type.EDGE||!selected.id().equals(SelectedElement.edge(e.firstNodeId(),e.secondNodeId()).id())))continue;Vector3d a=nodes.get(e.firstNodeId()).position(),b=nodes.get(e.secondNodeId()).position(),mid=new Vector3d(a).add(b).mul(.5);Vector4f q=new Vector4f((float)mid.x,(float)mid.y,(float)mid.z,1).mul(matrix);if(q.w>0)addNumber(glyphs,String.format(java.util.Locale.ROOT,"%.4f",rm.scientificCoordinates().get(e.firstNodeId()).distance(rm.scientificCoordinates().get(e.secondNodeId()))),q.x/q.w,q.y/q.w,px,py);}}GL20.glUniformMatrix4fv(matrixLocation,false,new Matrix4f().get(new float[16]));draw(glyphs,GL11.GL_LINES,2);GL20.glUniformMatrix4fv(matrixLocation,false,matrix.get(new float[16]));}
    private void addNumber(List<Float> v,String text,float x,float y,float px,float py){float cursor=x;for(char ch:text.toCharArray()){if(Character.isDigit(ch))addDigit(v,ch-'0',cursor,y,px,py);else if(ch=='.'){screenVertex(v,cursor+2*px,y);screenVertex(v,cursor+2.5f*px,y+.5f*py);}cursor+=7*px;}}
    private void addLetterC(List<Float> v,float x,float y,float px,float py){screenLine(v,x+5*px,y+10*py,x,y+10*py);screenLine(v,x,y+10*py,x,y);screenLine(v,x,y,x+5*px,y);}
    private void screenLine(List<Float> v,float ax,float ay,float bx,float by){screenVertex(v,ax,ay);screenVertex(v,bx,by);}
    private static Vector3d centroid(DerivedFace face,Map<Integer,DiscoveryNode> nodes){Vector3d c=new Vector3d();face.nodeIds().forEach(id->c.add(nodes.get(id).position()));return c.div(face.nodeIds().size());}
    private static Vector3d normal(DerivedFace face,Map<Integer,DiscoveryNode> nodes){if(face.nodeIds().size()<3)return new Vector3d();Vector3d a=nodes.get(face.nodeIds().get(0)).position(),b=nodes.get(face.nodeIds().get(1)).position(),c=nodes.get(face.nodeIds().get(2)).position();Vector3d n=new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a));return n.lengthSquared()<1e-24?n.zero():n.normalize();}

    public Matrix4f viewProjection(ViewState view, ViewFit fit,
                                   int width, int height) {
        double yaw = Math.toRadians(view.yaw());
        double pitch = Math.toRadians(view.pitch());
        Vector3f center = new Vector3f((float) view.center().x, (float) view.center().y,
                (float) view.center().z);
        Vector3f eye = new Vector3f(
                center.x + (float) (view.distance() * Math.cos(pitch) * Math.sin(yaw)),
                center.y + (float) (view.distance() * Math.sin(pitch)),
                center.z + (float) (view.distance() * Math.cos(pitch) * Math.cos(yaw)));
        float aspect = Math.max(1, width) / (float) Math.max(1, height);
        float dynamicNear = (float) Math.max(0.001, view.distance() - fit.radius() * 1.5);
        float dynamicFar = (float) Math.max(dynamicNear + 1,
                view.distance() + fit.radius() * 3.0);
        Matrix4f projection = new Matrix4f();
        if (view.orthographic()) {
            float size = (float) Math.max(1, fit.radius() * 1.5);
            projection.setOrtho(-size * aspect, size * aspect, -size, size,
                    dynamicNear, dynamicFar);
        } else {
            projection.setPerspective((float) Math.toRadians(45), aspect,
                    dynamicNear, dynamicFar);
        }
        return projection.mul(new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0)));
    }

    private void addConnections(List<Float> tooShort, List<Float> satisfied,
                                List<Float> tooLong, GeometryDiscoveryModel model,
                                GeometryDiscoverySettings settings, boolean errorColoring) {
        Map<Integer, DiscoveryNode> byId = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) byId.put(node.id(), node);
        for (DiscoveryConnection connection : model.connections()) {
            DiscoveryNode a = byId.get(connection.firstNodeId());
            DiscoveryNode b = byId.get(connection.secondNodeId());
            if (a == null || b == null) continue;
            double distance = a.position().distance(b.position());
            double signedError = (distance - settings.targetDistance())
                    / settings.targetDistance();
            double tolerance = Math.max(1e-9, settings.convergenceTolerance());
            List<Float> vertices = Math.abs(signedError) <= tolerance ? satisfied
                    : signedError < 0 ? tooShort : tooLong;
            float r = !errorColoring ? .65f : signedError > tolerance ? .95f : .2f;
            float g = !errorColoring ? .7f
                    : Math.abs(signedError) <= tolerance ? .9f : .45f;
            float blue = !errorColoring ? .25f : signedError < -tolerance ? 1f : .2f;
            vertex(vertices, a.position(), r, g, blue);
            vertex(vertices, b.position(), r, g, blue);
        }
    }

    private void addAxes(List<Float> v, ViewFit fit) {
        double length = Math.max(1, fit.radius() * 1.5);
        Vector3d o = new Vector3d();
        line(v, o, new Vector3d(length, 0, 0), 1, 0.2f, 0.2f);
        line(v, o, new Vector3d(0, length, 0), 0.2f, 1, 0.2f);
        line(v, o, new Vector3d(0, 0, length), 0.2f, 0.5f, 1);
    }

    private void addGrid(List<Float> v, ViewFit fit) {
        double size = Math.max(1, Math.ceil(fit.radius()));
        double step = Math.max(1, Math.pow(10, Math.floor(Math.log10(size))) / 2);
        for (double p = -size; p <= size + 1e-9; p += step) {
            line(v, new Vector3d(-size, 0, p), new Vector3d(size, 0, p),
                    0.18f, 0.18f, 0.2f);
            line(v, new Vector3d(p, 0, -size), new Vector3d(p, 0, size),
                    0.18f, 0.18f, 0.2f);
        }
    }

    private void addBoundingBox(List<Float> v, GeometryDiscoveryModel model) {
        if (model.nodes().isEmpty()) return;
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        for (DiscoveryNode node : model.nodes()) {
            min.min(node.position());
            max.max(node.position());
        }
        Vector3d[] c = {
                new Vector3d(min.x, min.y, min.z), new Vector3d(max.x, min.y, min.z),
                new Vector3d(min.x, max.y, min.z), new Vector3d(max.x, max.y, min.z),
                new Vector3d(min.x, min.y, max.z), new Vector3d(max.x, min.y, max.z),
                new Vector3d(min.x, max.y, max.z), new Vector3d(max.x, max.y, max.z)};
        int[][] edges = {{0,1},{0,2},{1,3},{2,3},{4,5},{4,6},{5,7},{6,7},
                {0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) line(v, c[edge[0]], c[edge[1]], 0.18f, 0.62f, 0.68f);
    }

    private void line(List<Float> v, Vector3d a, Vector3d b, float r, float g, float blue) {
        vertex(v, a, r, g, blue);
        vertex(v, b, r, g, blue);
    }

    private void vertex(List<Float> v, Vector3d p, float r, float g, float b) {
        v.add((float) p.x); v.add((float) p.y); v.add((float) p.z);
        v.add(r); v.add(g); v.add(b);
    }

    private void draw(List<Float> vertices, int mode, float lineWidth) {
        if (vertices.isEmpty()) return;
        float[] data = new float[vertices.size()];
        for (int i = 0; i < data.length; i++) data[i] = vertices.get(i);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        GL11.glLineWidth(lineWidth);
        GL11.glDrawArrays(mode, 0, data.length / 6);
    }

    /** Screen-space line glyphs are camera-facing and retain a constant pixel size while zooming. */
    private void drawCanonicalLabels(GeometryDiscoveryModel model, Matrix4f viewProjection,
                                     int width, int height) {
        List<Float> glyphs = new ArrayList<>();
        float pixelX = 2f / Math.max(1, width), pixelY = 2f / Math.max(1, height);
        for (DiscoveryNode node : model.nodes()) {
            Vector4f projected = new Vector4f((float)node.position().x, (float)node.position().y,
                    (float)node.position().z, 1).mul(viewProjection);
            if (projected.w <= 0) continue;
            float x = projected.x / projected.w + 8 * pixelX;
            float y = projected.y / projected.w + 6 * pixelY;
            String text = Integer.toString(node.id());
            for (int i=0;i<text.length();i++) addDigit(glyphs, text.charAt(i)-'0',
                    x+i*7*pixelX, y, pixelX, pixelY);
        }
        GL20.glUniformMatrix4fv(matrixLocation, false, new Matrix4f().get(new float[16]));
        draw(glyphs, GL11.GL_LINES, 2f);
        GL20.glUniformMatrix4fv(matrixLocation, false, viewProjection.get(new float[16]));
    }

    private void addDigit(List<Float> values, int digit, float x, float y, float px, float py) {
        int mask = new int[]{0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f}[digit];
        float[][] segments={{0,10,5,10},{5,10,5,5},{5,5,5,0},{0,0,5,0},
                {0,5,0,0},{0,10,0,5},{0,5,5,5}};
        for(int s=0;s<7;s++)if((mask&(1<<s))!=0){float[] q=segments[s];
            screenVertex(values,x+q[0]*px,y+q[1]*py);screenVertex(values,x+q[2]*px,y+q[3]*py);}
    }

    private void screenVertex(List<Float> values,float x,float y){values.add(x);values.add(y);values.add(0f);values.add(.95f);values.add(.95f);values.add(.8f);}

    private int createProgram() {
        String vertex = """
                #version 330 core
                layout(location=0) in vec3 position;
                layout(location=1) in vec3 color;
                uniform mat4 viewProjection;
                out vec3 vertexColor;
                void main() {
                    gl_Position = viewProjection * vec4(position, 1.0);
                    vertexColor = color;
                }
                """;
        String fragment = """
                #version 330 core
                in vec3 vertexColor;
                uniform float opacity;
                out vec4 outputColor;
                void main() { outputColor = vec4(vertexColor, opacity); }
                """;
        int vertexShader = shader(GL20.GL_VERTEX_SHADER, vertex);
        int fragmentShader = shader(GL20.GL_FRAGMENT_SHADER, fragment);
        int result = GL20.glCreateProgram();
        GL20.glAttachShader(result, vertexShader);
        GL20.glAttachShader(result, fragmentShader);
        GL20.glLinkProgram(result);
        if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            throw new IllegalStateException(GL20.glGetProgramInfoLog(result));
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        return result;
    }

    private int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    @Override public void close() {
        GL20.glDeleteProgram(program);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
