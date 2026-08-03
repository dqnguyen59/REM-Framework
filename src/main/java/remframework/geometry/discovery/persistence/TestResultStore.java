package remframework.geometry.discovery.persistence;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** Small, human-readable result summary store kept separate from topology TOML. */
public final class TestResultStore {
    public Path save(Path configurationSetDirectory, TestResult result) throws IOException {
        Path directory = configurationSetDirectory.resolve("geometry-tests");
        Files.createDirectories(directory);
        Path file = directory.resolve(result.configurationId() + "-" + Instant.now().toEpochMilli() + ".toml");
        String text = "format_version = 3\nconfiguration_id = \"" + result.configurationId() + "\"\n"
                + "experiment_profile = \"" + (result.experimentProfile() == null ? "LEGACY_UNSPECIFIED" : result.experimentProfile()) + "\"\n"
                + "face_composition_mode = \"" + result.faceComposition().mode() + "\"\n"
                + "common_face_edge_count = " + (result.faceComposition().commonFaceEdgeCount() == null ? 0 : result.faceComposition().commonFaceEdgeCount()) + "\n"
                + "primary_parameter_mode = \"" + result.primaryParameterMode() + "\"\n"
                + "surface_topology_assumption = \"" + result.surfaceTopologyAssumption() + "\"\n"
                + "derived_vertex_count = " + (result.parameterDerivation() == null || result.parameterDerivation().derivedVertexCount() == null ? 0 : result.parameterDerivation().derivedVertexCount()) + "\n"
                + "derived_edge_count = " + (result.parameterDerivation() == null || result.parameterDerivation().derivedEdgeCount() == null ? 0 : result.parameterDerivation().derivedEdgeCount()) + "\n"
                + "derived_face_count = " + (result.parameterDerivation() == null || result.parameterDerivation().derivedFaceCount() == null ? 0 : result.parameterDerivation().derivedFaceCount()) + "\n"
                + "planarity_mode = \"" + (result.planarityMode() == null ? "LEGACY_UNSPECIFIED" : result.planarityMode()) + "\"\n"
                + "angle_condition_mode = \"" + (result.angleConditionMode() == null ? "LEGACY_UNSPECIFIED" : result.angleConditionMode()) + "\"\n"
                + "benchmark_preset = \"" + (result.benchmarkPreset() == null ? "NONE" : result.benchmarkPreset()) + "\"\n"
                + "requested_seed = " + result.requestedSeed() + "\n"
                + "realized_seed = " + (result.realization()==null?result.requestedSeed():result.realization().seed()) + "\n"
                + "realization_strategy = \"" + result.continuation().strategy() + "\"\n"
                + "realization_initialization = \"" + result.continuation().initialization() + "\"\n"
                + "spectral_presentation_mode = \"" + result.continuation().spectralInitialization().mode() + "\"\n"
                + "spectral_animation_duration_seconds = " + result.continuation().spectralInitialization().animationDurationSeconds() + "\n"
                + "spectral_animation_fps = " + result.continuation().spectralInitialization().animationFramesPerSecond() + "\n"
                + "spectral_guidance_iterations = " + result.continuation().spectralInitialization().guidanceIterations() + "\n"
                + "spectral_handover_iterations = " + result.continuation().spectralInitialization().handoverIterations() + "\n"
                + "spectral_guidance_strength = " + result.continuation().spectralInitialization().guidanceStrength() + "\n"
                + "spectral_presentation_mode = \"" + result.continuation().spectralInitialization().mode() + "\"\n"
                + "spectral_animation_duration_seconds = " + result.continuation().spectralInitialization().animationDurationSeconds() + "\n"
                + "spectral_animation_fps = " + result.continuation().spectralInitialization().animationFramesPerSecond() + "\n"
                + "spectral_guidance_iterations = " + result.continuation().spectralInitialization().guidanceIterations() + "\n"
                + "spectral_handover_iterations = " + result.continuation().spectralInitialization().handoverIterations() + "\n"
                + "spectral_guidance_strength = " + result.continuation().spectralInitialization().guidanceStrength() + "\n"
                + "continuation_schedule_id = \"" + result.continuation().scheduleId() + "\"\n"
                + "continuation_schedule_version = " + result.continuation().scheduleVersion() + "\n"
                + "continuation_stage = \"" + result.continuation().stageId() + "\"\n"
                + "continuation_stage_state = \"" + result.continuation().state() + "\"\n"
                + "continuation_ramp_factor = " + result.continuation().rampFactor() + "\n"
                + "eligibility_status = \"" + (result.eligibility() == null ? "NOT_RECORDED" : result.eligibility().status()) + "\"\n"
                + "eligibility_reason = \"" + escape(result.eligibility() == null ? "Legacy result" : result.eligibility().conclusion()) + "\"\n"
                + "status = \"" + result.status().code() + "\"\n"
                + "explanation = \"" + escape(result.status().explanation()) + "\"\n"
                + "realization_found = " + (result.realization() != null && result.realization().converged()) + "\n"
                + "unique_faces = " + (result.faceDerivation() != null && result.faceDerivation().unique()) + "\n"
                + "closed_boundary = " + (result.boundary() != null && result.boundary().closed()) + "\n"
                + "area_available = " + result.area().available() + "\n"
                + "volume_available = " + result.volume().available() + "\n"
                + "saved_at = \"" + Instant.now() + "\"\n";
        if(result.threeDConstraintStrengths()!=null)text+="face_planarity_restoring_strength = "+result.threeDConstraintStrengths().facePlanarity()+"\nface_angle_restoring_strength = "+result.threeDConstraintStrengths().faceAngles()+"\ndihedral_restoring_strength = "+result.threeDConstraintStrengths().dihedrals()+"\nface_plane_radius_restoring_strength = "+result.threeDConstraintStrengths().facePlaneRadii()+"\n";
        for(int i=0;i<result.continuation().history().size();i++){ContinuationStageOutcome outcome=result.continuation().history().get(i);text+="continuation_history_"+i+" = \""+escape(outcome.stageId()+"|"+outcome.state()+"|"+outcome.iterations()+"|"+outcome.worstConstraint()+"|"+outcome.worstRatio()+"|"+outcome.reason())+"\"\n";}
        if (result.referenceTopologyMatch() != null) {
            var match = result.referenceTopologyMatch();
            text += "reference_topology = \"" + match.topology() + "\"\n"
                    + "reference_exact_match = " + match.exactMatch() + "\n"
                    + "reference_matching_method = \"" + match.matchingMethod() + "\"\n"
                    + "reference_version = \"" + match.referenceVersion() + "\"\n"
                    + "reference_explanation = \"" + escape(match.explanation()) + "\"\n";
        }
        if (result.eligibility() != null) {
            int conditionIndex = 0;
            for (var condition : result.eligibility().conditions()) {
                text += "eligibility_condition_" + conditionIndex + " = \""
                        + escape(condition.condition() + "|" + condition.outcome() + "|" + condition.explanation()) + "\"\n";
                conditionIndex++;
            }
        }
        if (result.twoDRegularity() != null) {
            var metrics = result.twoDRegularity();
            text += "regularity_2d_area = " + metrics.area() + "\n"
                    + "regularity_2d_perimeter = " + metrics.perimeter() + "\n"
                    + "regularity_2d_circularity = " + metrics.circularity() + "\n"
                    + "regularity_2d_edge_variance = " + metrics.edgeLengths().variance() + "\n"
                    + "regularity_2d_angle_variance = " + metrics.interiorAngles().variance() + "\n"
                    + "regularity_2d_centroid_radius_variance = " + metrics.centroidRadii().variance() + "\n"
                    + "planarity_passed = " + metrics.planar() + "\n"
                    + "maximum_plane_deviation = " + metrics.maximumPlaneDeviation() + "\n"
                    + "mean_plane_deviation = " + metrics.meanPlaneDeviation() + "\n"
                    + "rms_plane_deviation = " + metrics.rmsPlaneDeviation() + "\n"
                    + "planarity_tolerance = " + metrics.planarityTolerance() + "\n"
                    + "target_interior_angle = " + metrics.targetInteriorAngle() + "\n"
                    + "target_turning_angle = " + metrics.targetTurningAngle() + "\n"
                    + "maximum_angle_target_deviation = " + metrics.maximumAngleTargetDeviation() + "\n";
            for(int i=0;i<metrics.interiorAngles().values().size();i++)
                text += "vertex_angle_"+i+" = ["+metrics.interiorAngles().values().get(i)+", "
                        +metrics.turningAngles().values().get(i)+", "+metrics.interiorAngleTargetDeviations().get(i)
                        +", "+metrics.turningAngleTargetDeviations().get(i)+", "+metrics.planeDistances().get(i)+"]\n";
        }
        if (result.threeDRegularity() != null) {
            var metrics = result.threeDRegularity();
            text += "geometry_center_definition = \""+metrics.centerDefinition()+"\"\n"
                    + "geometry_center = ["+metrics.geometryCenter().x+", "+metrics.geometryCenter().y+", "+metrics.geometryCenter().z+"]\n"
                    + "regularity_3d_applicable = "+metrics.applicable()+"\n"
                    + "regularity_3d_edge_variance = " + metrics.edgeLengths().variance() + "\n"
                    + "regularity_3d_dihedral_variance = " + metrics.interiorDihedralAngles().variance() + "\n"
                    + "regularity_3d_vertex_radius_variance = " + metrics.vertexRadii().variance() + "\n"
                    + "regularity_3d_face_center_radius_variance = " + metrics.faceCenterRadii().variance() + "\n"
                    + "regularity_3d_edge_midpoint_radius_variance = " + metrics.edgeMidpointRadii().variance() + "\n"
                    + "regularity_3d_face_plane_radius_variance = "+metrics.facePlaneRadii().variance()+"\n"
                    + "regularity_3d_normal_alignment_variance = "+metrics.normalCenterAlignments().variance()+"\n";
            for(int i=0;i<metrics.facePlanes().size();i++){var face=metrics.facePlanes().get(i);text+="face_plane_"+i+" = \""+escape(face.faceId()+"|"+face.planar()+"|"+face.maximumDeviation()+"|"+face.meanDeviation()+"|"+face.rmsDeviation()+"|"+face.facePlaneRadius()+"|"+face.normalCenterAlignment()+"|"+face.orientation())+"\"\n";}
            if(result.threeDConditionReport()!=null)text+="three_d_conditions = \""+escape(result.threeDConditionReport().conditions().toString())+"\"\nthree_d_condition_conclusions = \""+escape(result.threeDConditionReport().conclusions().toString())+"\"\n";
        }
        if (result.realization() != null) {
            for (int i = 0; i < result.realization().finalCoordinates().size(); i++) {
                var coordinate = result.realization().finalCoordinates().get(i);
                text += "coordinate_" + i + " = [" + coordinate.x + ", "
                        + coordinate.y + ", " + coordinate.z + "]\n";
            }
        }
        if(result.optimizationComparison()!=null)for(var entry:result.optimizationComparison().changes().entrySet())text+="optimization_change_"+entry.getKey()+" = ["+entry.getValue().baseline()+", "+entry.getValue().optimized()+", "+entry.getValue().absoluteChange()+", "+entry.getValue().relativeChange()+"]\n";
        text += "constraint_measurement_count = " + result.constraintMeasurements().size() + "\n";
        for (int i=0;i<result.constraintMeasurements().size();i++) {
            ConstraintMeasurement m=result.constraintMeasurements().get(i);
            text += "constraint_measurement_"+i+"_id = \""+escape(m.constraintId())+"\"\n"
                    +"constraint_measurement_"+i+"_state = \""+m.state()+"\"\n"
                    +"constraint_measurement_"+i+"_raw = "+(m.rawValue()==null?"null":m.rawValue())+"\n"
                    +"constraint_measurement_"+i+"_tolerance = "+(m.tolerance()==null?"null":m.tolerance())+"\n"
                    +"constraint_measurement_"+i+"_unit = \""+escape(m.unit())+"\"\n"
                    +"constraint_measurement_"+i+"_reason_code = \""+escape(m.reasonCode())+"\"\n"
                    +"constraint_measurement_"+i+"_reason = \""+escape(m.reasonText())+"\"\n"
                    +"constraint_measurement_"+i+"_qualitative_state = \""+escape(m.qualitativeState())+"\"\n"
                    +"constraint_measurement_"+i+"_samples = "+m.sampleCount()+"\n"
                    +"constraint_measurement_"+i+"_invalid_samples = "+m.invalidSampleCount()+"\n"
                    +"constraint_measurement_"+i+"_coordinate_revision = "+m.coordinateRevision()+"\n";
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    public StoredResult load(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator > 0) values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        java.util.List<org.joml.Vector3d> coordinates = new java.util.ArrayList<>();
        for (int index = 0; values.containsKey("coordinate_" + index); index++) {
            String[] parts = values.get("coordinate_" + index).replace("[", "")
                    .replace("]", "").split(",");
            coordinates.add(new org.joml.Vector3d(Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())));
        }
        java.util.List<ConstraintMeasurement> measurements=new java.util.ArrayList<>();
        int measurementCount=Integer.parseInt(values.getOrDefault("constraint_measurement_count","0"));
        for(int i=0;i<measurementCount;i++){
            String id=unquote(values.get("constraint_measurement_"+i+"_id"));
            MeasurementState state=MeasurementState.valueOf(unquote(values.get("constraint_measurement_"+i+"_state")));
            String rawText=values.get("constraint_measurement_"+i+"_raw"),toleranceText=values.get("constraint_measurement_"+i+"_tolerance");
            Double raw="null".equals(rawText)||rawText==null?null:Double.parseDouble(rawText),tol="null".equals(toleranceText)||toleranceText==null?null:Double.parseDouble(toleranceText);
            measurements.add(new ConstraintMeasurement(id,state,raw,tol,null,unquote(values.get("constraint_measurement_"+i+"_unit")),
                    unquote(values.get("constraint_measurement_"+i+"_reason_code")),unquote(values.get("constraint_measurement_"+i+"_reason")),List.of(),
                    Integer.parseInt(values.getOrDefault("constraint_measurement_"+i+"_samples","0")),Integer.parseInt(values.getOrDefault("constraint_measurement_"+i+"_invalid_samples","0")),Long.parseLong(values.getOrDefault("constraint_measurement_"+i+"_coordinate_revision","0")),unquote(values.getOrDefault("constraint_measurement_"+i+"_qualitative_state","\"\""))));
        }
        if(measurementCount==0&&values.values().stream().anyMatch(v->v.equals("NaN")))measurements.add(ConstraintMeasurement.legacy("legacy-measurement",Double.NaN,null,"",0));
        return new StoredResult(new ConfigurationId(unquote(values.get("configuration_id"))),
                GeometryConfigurationStatusCode.valueOf(unquote(values.get("status"))),
                unquote(values.get("explanation")), Boolean.parseBoolean(values.get("realization_found")),
                Boolean.parseBoolean(values.get("unique_faces")), Boolean.parseBoolean(values.get("closed_boundary")),
                Boolean.parseBoolean(values.get("area_available")), Boolean.parseBoolean(values.get("volume_available")),
                Instant.parse(unquote(values.get("saved_at"))), coordinates,measurements);
    }

    private String unquote(String value) { return value != null && value.startsWith("\"") ? value.substring(1, value.length() - 1) : value; }
    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
