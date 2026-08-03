package remframework.geometry.discovery.research.benchmark;

import java.nio.file.*;

/** Post-run-only persistence for descriptive reference metrics. */
public final class BenchmarkEvaluationStore {
    private final Path path;
    public BenchmarkEvaluationStore(Path root){path=root.resolve("reference-evaluation.csv");}
    public synchronized void append(PostRunBenchmarkEvaluation e)throws Exception{Files.createDirectories(path.toAbsolutePath().getParent());if(Files.notExists(path))Files.writeString(path,"configuration,reference,exact_topology,face_count,face_size,regularity_similarity,explanation\n");Files.writeString(path,String.join(",",e.configurationId(),e.topologyReference(),Boolean.toString(e.exactTopologyMatch()),Integer.toString(e.faceCount()),Integer.toString(e.uniformFaceSize()),Double.toString(e.coordinateIndependentRegularitySimilarity()),quote(e.explanation()))+"\n",StandardOpenOption.APPEND);}
    private static String quote(String value){return '"'+value.replace("\"","\"\"")+'"';}
}
