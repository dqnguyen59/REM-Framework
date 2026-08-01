package remframework.measurement.detector;
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

import org.ini4j.Wini;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DetectorInfrastructureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void addingDetectorDoesNotAddIdentity() {
        int before = Project.getInstance().getOccupancies().size();
        new Detector("D", new Vector3i(), null, null, null);
        assertEquals(before, Project.getInstance().getOccupancies().size());
    }

    @Test
    void addingDetectorDoesNotAddOccupancy() {
        List<Detector> detectors = List.of(
                new Detector("D", new Vector3i(), null, null, null));
        assertEquals(1, detectors.size());
        assertTrue(Project.getInstance().getOccupancies().isEmpty());
    }

    @Test
    void detectorReadDoesNotMutateNode() {
        Node center = symmetricNode();
        Vector3d position = new Vector3d(center.getPosition());
        Vector3d positionBuffer = new Vector3d(center.getPosBuff());
        Vector3d velocity = new Vector3d(center.getVelocity());
        Vector3d acceleration = new Vector3d(center.getAcceleration());
        Vector3d force = new Vector3d(center.getForce());
        Vector3d cachedGradient = new Vector3d(center.getGradientImbalance());
        double[] flow = center.getFlow().clone();
        double[] previousFlow = center.getPreviousFlow().clone();
        double[] deltaFlow = center.getDeltaFlow().clone();
        Node[] neighbors = center.getNeighbors().clone();
        Vector3d[] deltaPositions = copy(center.getDeltaPositions());
        Vector3d[] deltaVelocity = copy(center.getDeltaVelocity());

        assertEquals(new Vector3d(), center.readGradientImbalance());
        assertEquals(position, center.getPosition());
        assertEquals(positionBuffer, center.getPosBuff());
        assertEquals(velocity, center.getVelocity());
        assertEquals(acceleration, center.getAcceleration());
        assertEquals(force, center.getForce());
        assertEquals(cachedGradient, center.getGradientImbalance());
        assertArrayEquals(flow, center.getFlow());
        assertArrayEquals(previousFlow, center.getPreviousFlow());
        assertArrayEquals(deltaFlow, center.getDeltaFlow());
        assertArrayEquals(neighbors, center.getNeighbors());
        assertVectorArrayEquals(deltaPositions, center.getDeltaPositions());
        assertVectorArrayEquals(deltaVelocity, center.getDeltaVelocity());
    }

    @Test
    void simulationsWithAndWithoutDetectorReadsHaveIdenticalState() {
        Node unobserved = symmetricNode();
        Node observed = symmetricNode();
        observed.readGradientImbalance();
        observed.readGradientImbalance();
        assertEquals(unobserved.getPosition(), observed.getPosition());
        assertEquals(unobserved.getVelocity(), observed.getVelocity());
        assertEquals(unobserved.getAcceleration(), observed.getAcceleration());
    }

    @Test
    void multipleDetectorsCanReadSameNode() throws Exception {
        Node node = symmetricNode();
        prepareSingleNodeLattice(node);
        Detector first = new Detector("A", new Vector3i(), null, null, null);
        Detector second = new Detector("B", new Vector3i(), null, null, null);
        DetectorMeasurementLogger logger = new DetectorMeasurementLogger();
        Path output = temporaryDirectory.resolve("same-node.csv");
        logger.configure(output, List.of(first, second), null);
        logger.sample(Lattice.getInstance(), Double.NaN);
        logger.close();
        assertEquals(3, Files.readAllLines(output).size());
        assertEquals(new Vector3d(), node.readGradientImbalance());
    }

    @Test
    void invalidCoordinatesFailClearly() {
        Wini ini = detectorIni(1);
        putDetector(ini, 0, "outside", 3, 0, 0);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DetectorConfiguration.load(ini, new Vector3i(2, 2, 1)));
        assertTrue(exception.getMessage().contains("outside lattice"));
    }

    @Test
    void duplicateNamesFailClearly() {
        Wini ini = detectorIni(2);
        putDetector(ini, 0, "duplicate", 0, 0, 0);
        putDetector(ini, 1, "duplicate", 1, 0, 0);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DetectorConfiguration.load(ini, new Vector3i(2, 2, 1)));
        assertTrue(exception.getMessage().contains("Duplicate detector name"));
    }

    @Test
    void csvContainsOneRowPerDetectorPerUpdate() throws Exception {
        Node node = symmetricNode();
        prepareSingleNodeLattice(node);
        DetectorMeasurementLogger logger = new DetectorMeasurementLogger();
        Path output = temporaryDirectory.resolve("rows.csv");
        logger.configure(output, List.of(
                new Detector("A", new Vector3i(), null, null, null),
                new Detector("B", new Vector3i(), null, null, null)), null);
        logger.sample(Lattice.getInstance(), Double.NaN);
        logger.sample(Lattice.getInstance(), Double.NaN);
        logger.close();
        assertEquals(1 + 2 * 2, Files.readAllLines(output).size());
    }

    @Test
    void emptyEquilibriumReadsNumericalZero() {
        Node node = symmetricNode();
        assertEquals(0.0, node.readGradientImbalance().length());
        assertEquals(0.0, new Vector3d(node.getPosition())
                .sub(node.getEquilibriumPosition()).length());
        assertEquals(0.0, node.getVelocity().length());
        assertEquals(0.0, node.getAcceleration().length());
    }

    @Test
    void projectWithoutDetectorsLoadsUnchangedConfiguration() {
        List<Detector> detectors = DetectorConfiguration.load(
                new Wini(), new Vector3i(3, 3, 1));
        assertTrue(detectors.isEmpty());
    }

    @Test
    void missingRequiredPropertiesFailClearly() {
        Wini ini = detectorIni(1);
        ini.put("Detector0", "nodeIndex.x", 0);
        ini.put("Detector0", "nodeIndex.y", 0);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DetectorConfiguration.load(ini, new Vector3i(1, 1, 1)));
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    void test2dDefines112UniqueExactAxisDetectors() throws Exception {
        Wini ini = new Wini(new File(
                "samples/gravity-test-2/gravity-test-2d-radial-falloff.qfs"));
        List<Detector> detectors = DetectorConfiguration.load(
                ini, new Vector3i(1201, 1201, 1));
        assertEquals(112, detectors.size());
        Set<String> names = new HashSet<>();
        for (Detector detector : detectors) {
            assertTrue(names.add(detector.getName()));
            Vector3i index = detector.getNodeIndex();
            int dx = index.x - 600;
            int dy = index.y - 600;
            assertEquals(detector.getRequestedRadius().intValue(),
                    Math.abs(dx) + Math.abs(dy));
            assertTrue(dx == 0 || dy == 0);
            assertEquals(detector.getRequestedRadius().doubleValue(),
                    Math.hypot(dx, dy));
            assertTrue(index.x >= 30 && index.x <= 1170);
            assertTrue(index.y >= 30 && index.y <= 1170);
        }
        assertEquals(1, Integer.parseInt(ini.get("Oscillators", "Size")));
        assertEquals("true", ini.get("Oscillator0", "active.z"));
        assertEquals("false", ini.get("Oscillator0", "active.x"));
        assertEquals("false", ini.get("Oscillator0", "active.y"));
    }

    @Test
    void test2dRawCollectionAndCycleScheduleAreComplete() throws Exception {
        Path raw = Path.of(
                "measurements/gravity-test-2d-radial-falloff-run-001.csv");
        Assumptions.assumeTrue(Files.exists(raw),
                "External Test 2D raw evidence is not present");
        List<String> lines = Files.readAllLines(raw);
        assertEquals(107_521, lines.size());
        String[] header = lines.getFirst().split(",");
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) columns.put(header[i], i);
        Map<String, boolean[]> updates = new HashMap<>();
        Map<String, Integer> previousCompletion = new HashMap<>();
        Set<Integer> reflectionEstimates = new HashSet<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String[] row = lines.get(lineIndex).split(",", -1);
            String name = row[columns.get("detectorName")];
            int update = Integer.parseInt(row[columns.get("update")]);
            boolean[] sequence = updates.computeIfAbsent(
                    name, ignored -> new boolean[961]);
            assertFalse(sequence[update], name + " duplicate update " + update);
            sequence[update] = true;
            for (String field : header) {
                if (field.equals("detectorName")
                        || field.equals("direction")
                        || field.equals("cycleCompleted")) continue;
                double value = Double.parseDouble(row[columns.get(field)]);
                assertTrue(Double.isFinite(value), name + " " + field);
            }
            assertEquals(0.0, Double.parseDouble(
                    row[columns.get("coordinateRoundingErrorMagnitude")]));
            if (Boolean.parseBoolean(row[columns.get("cycleCompleted")])) {
                Integer previous = previousCompletion.put(name, update);
                if (previous != null) assertEquals(18, update - previous);
            }
            int radius = Integer.parseInt(row[columns.get("requestedRadius")]);
            reflectionEstimates.add(1143 - radius);
        }
        assertEquals(112, updates.size());
        for (Map.Entry<String, boolean[]> entry : updates.entrySet()) {
            for (int update = 1; update <= 960; update++) {
                assertTrue(entry.getValue()[update],
                        entry.getKey() + " missing update " + update);
            }
        }
        assertTrue(reflectionEstimates.size() > 1);
    }

    @Test
    void test2eDefinesCenteredSixAxisThreeDimensionalDetectors()
            throws Exception {
        Wini ini = new Wini(new File(
                "samples/gravity-test-2/gravity-test-2e-3d-pilot.qfs"));
        List<Detector> detectors = DetectorConfiguration.load(
                ini, new Vector3i(161, 161, 161));
        assertEquals(54, detectors.size());
        assertEquals(80, GravityTest2EPilotMath.centerOfOddLattice(161));
        Set<String> names = new HashSet<>();
        Map<String, Integer> directions = new HashMap<>();
        for (Detector detector : detectors) {
            assertTrue(names.add(detector.getName()));
            directions.merge(detector.getDirection(), 1, Integer::sum);
            Vector3i index = detector.getNodeIndex();
            int dx = index.x - 80;
            int dy = index.y - 80;
            int dz = index.z - 80;
            int nonZeroAxes = (dx == 0 ? 0 : 1)
                    + (dy == 0 ? 0 : 1) + (dz == 0 ? 0 : 1);
            assertEquals(1, nonZeroAxes);
            assertEquals(detector.getRequestedRadius().doubleValue(),
                    Math.sqrt((double) dx * dx + (double) dy * dy
                            + (double) dz * dz));
            assertTrue(index.x >= 30 && index.x <= 130);
            assertTrue(index.y >= 30 && index.y <= 130);
            assertTrue(index.z >= 30 && index.z <= 130);
        }
        assertEquals(Set.of("PX", "NX", "PY", "NY", "PZ", "NZ"),
                directions.keySet());
        assertTrue(directions.values().stream().allMatch(count -> count == 9));
    }

    @Test
    void test2eThreeDimensionalProjectionAndTransverseMagnitude() {
        Vector3d radial = GravityTest2EPilotMath.radialUnitVector(
                new Vector3i(80, 80, 80), new Vector3i(80, 80, 85));
        assertEquals(new Vector3d(0, 0, 1), radial);
        Vector3d value = new Vector3d(3, 4, 12);
        assertEquals(12.0,
                GravityTest2EPilotMath.radialProjection(value, radial));
        assertEquals(5.0,
                GravityTest2EPilotMath.transverseMagnitude(value, radial));
    }

    @Test
    void test2eArrivalAndReflectionCycleHelpersAreConservative() {
        assertEquals(6, GravityTest2EPilotMath.nextCompleteCycle(5));
        assertEquals(96, GravityTest2EPilotMath.reflectionEstimate(95.0));
        assertEquals(4, GravityTest2EPilotMath.reflectionSafeCycles(
                18, 96, 18));
        assertEquals(0, GravityTest2EPilotMath.reflectionSafeCycles(
                90, 56, 18));
    }

    @Test
    void test2eRejectsInvalidGeometryAndFitValues() {
        assertThrows(IllegalArgumentException.class,
                () -> GravityTest2EPilotMath.centerOfOddLattice(160));
        assertThrows(IllegalArgumentException.class,
                () -> GravityTest2EPilotMath.radialUnitVector(
                        new Vector3i(1, 1, 1), new Vector3i(1, 1, 1)));
        assertFalse(GravityTest2EPilotMath.validPositiveFitValue(0.0));
        assertFalse(GravityTest2EPilotMath.validPositiveFitValue(-1.0));
        assertFalse(GravityTest2EPilotMath.validPositiveFitValue(Double.NaN));
        assertFalse(GravityTest2EPilotMath.validPositiveFitValue(
                Double.POSITIVE_INFINITY));
        assertTrue(GravityTest2EPilotMath.validPositiveFitValue(1.0));
    }

    @Test
    void test2eRawCollectionIsCompleteAndFinite() throws Exception {
        Path raw = Path.of("measurements", "gravity-test-2e-3d-pilot",
                "raw-detectors-run-001.csv");
        Assumptions.assumeTrue(Files.exists(raw),
                "External Test 2E pilot evidence is not present");
        List<String> lines = Files.readAllLines(raw);
        assertEquals(9_721, lines.size());
        String[] header = lines.getFirst().split(",");
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) columns.put(header[i], i);
        Map<String, boolean[]> updates = new HashMap<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String[] row = lines.get(lineIndex).split(",", -1);
            String name = row[columns.get("detectorName")];
            int update = Integer.parseInt(row[columns.get("update")]);
            boolean[] sequence = updates.computeIfAbsent(
                    name, ignored -> new boolean[181]);
            assertFalse(sequence[update], name + " duplicate " + update);
            sequence[update] = true;
            for (String field : header) {
                if (field.equals("detectorName")
                        || field.equals("direction")
                        || field.equals("cycleCompleted")
                        || row[columns.get(field)].isEmpty()) continue;
                assertTrue(Double.isFinite(Double.parseDouble(
                        row[columns.get(field)])), name + " " + field);
            }
        }
        assertEquals(54, updates.size());
        for (Map.Entry<String, boolean[]> entry : updates.entrySet()) {
            for (int update = 1; update <= 180; update++) {
                assertTrue(entry.getValue()[update],
                        entry.getKey() + " missing " + update);
            }
        }
    }

    @Test
    void experiment3aGeneratesTwentySixClassifiedOppositeDirections() {
        List<Experiment3AGeometry.DirectionSpec> directions =
                Experiment3AGeometry.highSymmetryDirections();
        assertEquals(26, directions.size());
        assertEquals(6, directions.stream().filter(direction ->
                direction.directionClass()
                        == Experiment3AGeometry.DirectionClass.AXIS).count());
        assertEquals(12, directions.stream().filter(direction ->
                direction.directionClass()
                        == Experiment3AGeometry.DirectionClass.FACE_DIAGONAL)
                .count());
        assertEquals(8, directions.stream().filter(direction ->
                direction.directionClass()
                        == Experiment3AGeometry.DirectionClass.BODY_DIAGONAL)
                .count());
        Set<String> labels = new HashSet<>();
        directions.forEach(direction -> {
            assertTrue(labels.add(direction.label()));
            assertEquals(1.0, direction.unitDirection().length(), 1e-15);
        });
        directions.forEach(direction -> assertEquals(1,
                directions.stream().filter(candidate ->
                        candidate.label().equals(direction.oppositeLabel()))
                        .count()));
    }

    @Test
    void experiment3aRadiusTwentyPlacementIsUniqueAndExact() {
        Map<Vector3i, Experiment3AGeometry.DirectionSpec> placements =
                Experiment3AGeometry.uniquePlacements(20);
        assertEquals(26, placements.size());
        placements.forEach((offset, direction) -> {
            assertTrue(offset.lengthSquared() > 0);
            Vector3d actual = new Vector3d(offset.x, offset.y, offset.z);
            assertEquals(actual.length(),
                    Math.sqrt((double) offset.x * offset.x
                            + (double) offset.y * offset.y
                            + (double) offset.z * offset.z));
            assertTrue(actual.normalize().dot(direction.unitDirection())
                    > 0.999);
        });
    }

    @Test
    void experiment3aAnglesProjectionAndParityUseGeometry() {
        assertEquals(0.0, Experiment3AGeometry.thetaDegrees(
                new Vector3d(0, 0, 1)));
        assertEquals(180.0, Experiment3AGeometry.thetaDegrees(
                new Vector3d(0, 0, -1)));
        assertEquals(90.0, Experiment3AGeometry.thetaDegrees(
                new Vector3d(1, 0, 0)));
        assertEquals(90.0, Experiment3AGeometry.phiDegrees(
                new Vector3d(0, 1, 0)));
        assertEquals(-90.0, Experiment3AGeometry.phiDegrees(
                new Vector3d(0, -1, 0)));
        assertEquals(-3.0, Experiment3AGeometry.radialProjection(
                new Vector3d(3, 0, 0), new Vector3d(-1, 0, 0)));
        assertEquals(0.0, Experiment3AGeometry.evenDifference(2.0, 2.0));
        assertEquals(0.0, Experiment3AGeometry.oddResidual(2.0, -2.0));
        assertTrue(Experiment3AGeometry.evenDifference(2.0, -2.0) > 1.0);
        assertEquals(List.of(18),
                Experiment3AGeometry.completeCycleEndUpdates(35, 18));
        assertThrows(IllegalArgumentException.class,
                () -> Experiment3AGeometry.completeCycleEndUpdates(35, 0));
    }

    @Test
    void experiment3aProjectCreatesUniqueShellAndOutputsWhenPresent()
            throws Exception {
        Wini ini = new Wini(new File(
                "samples/experiment-3a-angular-characterization.qfs"));
        List<Detector> detectors = DetectorConfiguration.load(
                ini, new Vector3i(161, 161, 161));
        assertEquals(26, detectors.size());
        assertEquals(26, detectors.stream().map(Detector::getNodeIndex)
                .map(Vector3i::new).collect(java.util.stream.Collectors.toSet())
                .size());

        Path output = Path.of("measurements",
                "experiment-3a-angular-characterization");
        Assumptions.assumeTrue(Files.exists(output.resolve("report.md")),
                "Experiment 3A generated evidence is not present");
        assertTrue(Files.size(output.resolve("detector-geometry.csv")) > 0);
        assertTrue(Files.size(output.resolve("detector-cycle-summary.csv")) > 0);
        for (String chart : List.of(
                "angular-radial-acceleration",
                "angular-acceleration-magnitude",
                "angular-raw-radial-gradient",
                "angular-displacement-magnitude",
                "azimuthal-response", "opposite-even-parity",
                "opposite-odd-parity", "direction-class-comparison",
                "representative-radial-acceleration",
                "representative-acceleration-magnitude",
                "directional-response-3d")) {
            assertTrue(Files.size(output.resolve(
                    "charts/" + chart + ".png")) > 20_000);
            assertTrue(Files.size(output.resolve(
                    "charts/" + chart + ".svg")) > 2_000);
        }
    }

    @Test
    void experiment3bConfigurationsRotateOnlyTheSourceAmplitude()
            throws Exception {
        Map<String, Vector3d> expected = Map.of(
                "x", new Vector3d(200, 0, 0),
                "y", new Vector3d(0, 200, 0),
                "z", new Vector3d(0, 0, 200));
        Set<Set<Vector3i>> placements = new HashSet<>();
        for (Map.Entry<String, Vector3d> entry : expected.entrySet()) {
            Wini ini = new Wini(new File(
                    "samples/experiment-3b-rotation-"
                            + entry.getKey() + ".qfs"));
            Vector3d amplitude = new Vector3d(
                    ini.get("Oscillator0", "amplitude.x", double.class),
                    ini.get("Oscillator0", "amplitude.y", double.class),
                    ini.get("Oscillator0", "amplitude.z", double.class));
            assertEquals(entry.getValue(), amplitude);
            List<Detector> detectors = DetectorConfiguration.load(
                    ini, new Vector3i(161, 161, 161));
            assertEquals(26, detectors.size());
            placements.add(detectors.stream().map(Detector::getNodeIndex)
                    .map(Vector3i::new).collect(
                            java.util.stream.Collectors.toSet()));
        }
        assertEquals(1, placements.size());
    }

    @Test
    void experiment3bRotationIsInvertibleAndPreservesGeometry() {
        for (Experiment3BRotation.SourceAxis axis
                : Experiment3BRotation.SourceAxis.values()) {
            for (Experiment3AGeometry.DirectionSpec direction
                    : Experiment3AGeometry.highSymmetryDirections()) {
                Vector3i canonical =
                        Experiment3BRotation.canonicalComponents(
                                direction.requestedDirection(), axis);
                Vector3i restored = Experiment3BRotation.latticeComponents(
                        canonical, axis);
                assertEquals(direction.requestedDirection(), restored);
                assertEquals(direction.requestedDirection().lengthSquared(),
                        canonical.lengthSquared());
                assertTrue(Experiment3AGeometry.highSymmetryDirections()
                        .stream().anyMatch(candidate ->
                                candidate.requestedDirection()
                                        .equals(canonical)));
                Vector3i opposite = new Vector3i(
                        direction.requestedDirection()).negate();
                assertEquals(new Vector3i(canonical).negate(),
                        Experiment3BRotation.canonicalComponents(
                                opposite, axis));
            }
        }
    }

    @Test
    void experiment3bSourceRelativeAnglesAndDecompositionAreCorrect() {
        usingAxis(Experiment3BRotation.SourceAxis.X,
                new Vector3d(1, 0, 0));
        usingAxis(Experiment3BRotation.SourceAxis.Y,
                new Vector3d(0, 1, 0));
        usingAxis(Experiment3BRotation.SourceAxis.Z,
                new Vector3d(0, 0, 1));
        assertEquals(0.0, Experiment3BRotation.sourceAzimuthDegrees(
                new Vector3d(0, 1, 0),
                Experiment3BRotation.SourceAxis.X));
        assertEquals(90.0, Experiment3BRotation.sourceAzimuthDegrees(
                new Vector3d(0, 0, 1),
                Experiment3BRotation.SourceAxis.X));
        assertEquals(new Vector3i(1, 0, 0),
                Experiment3BRotation.canonicalComponents(
                        new Vector3i(0, 0, 1),
                        Experiment3BRotation.SourceAxis.Z));
        assertEquals(new Vector3i(1, 0, 0),
                Experiment3BRotation.canonicalComponents(
                        new Vector3i(1, 0, 0),
                        Experiment3BRotation.SourceAxis.X));
        assertEquals(new Vector3i(1, 0, 0),
                Experiment3BRotation.canonicalComponents(
                        new Vector3i(0, 1, 0),
                        Experiment3BRotation.SourceAxis.Y));
        assertEquals(3.0, Experiment3BRotation.longitudinal(
                new Vector3d(3, 4, 0),
                Experiment3BRotation.SourceAxis.X));
        assertEquals(4.0, Experiment3BRotation.transverseMagnitude(
                new Vector3d(3, 4, 0),
                Experiment3BRotation.SourceAxis.X));
    }

    @Test
    void experiment3bResidualStatisticsAndGeneratedEvidence() throws Exception {
        assertEquals(0.0, Experiment3BRotation.coefficientOfVariation(
                2.0, 2.0, 2.0));
        assertEquals(1.0, Experiment3BRotation.maximumRelativeSpread(
                1.0, 2.0, 3.0));
        assertThrows(IllegalArgumentException.class,
                () -> Experiment3BRotation.coefficientOfVariation(
                        1.0, Double.NaN));
        Path root = Path.of("measurements",
                "experiment-3b-rotational-consistency");
        Assumptions.assumeTrue(Files.exists(root.resolve("combined/report.md")),
                "Experiment 3B generated evidence is not present");
        for (String run : List.of("run-x", "run-y", "run-z")) {
            assertTrue(Files.size(root.resolve(
                    run + "/detector-cycle-summary.csv")) > 0);
        }
        assertTrue(Files.size(root.resolve(
                "combined/rotational-residuals.csv")) > 0);
        assertTrue(Files.size(root.resolve(
                "charts/chart-validation.txt")) > 0);
    }

    @Test
    void experiment3cCompositeDefinitionsPreserveSignedValues() {
        assertEquals(2.0, Experiment3CComposite.arithmeticMean(1, 2, 3));
        assertEquals(Math.sqrt(14.0 / 3.0),
                Experiment3CComposite.pooledRms(1, 2, 3));
        assertEquals(-2.0,
                Experiment3CComposite.arithmeticMean(-1, -2, -3));
        assertEquals(0.0,
                Experiment3CComposite.coefficientOfVariation(4, 4, 4));
        assertEquals(1.0,
                Experiment3CComposite.relativeSpread(1, 2, 3));
        assertEquals(12.0,
                Experiment3CComposite.radiusNormalize(3, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> Experiment3CComposite.pooledRms(Double.NaN));
    }

    @Test
    void experiment3cSyntheticCasesAndGeneratedEvidence() throws Exception {
        double[] isotropic = {5, 5, 5, 5, 5, 5};
        assertEquals(0.0,
                Experiment3CComposite.coefficientOfVariation(isotropic));
        double[] cubic = {1, 1, 2, 2, 3, 3};
        assertTrue(Experiment3CComposite.coefficientOfVariation(cubic) > 0);
        double[] radii = {10, 20, 40};
        double[] radiusOnly = {1.0 / 10, 1.0 / 20, 1.0 / 40};
        double[] normalized = new double[3];
        for (int i = 0; i < normalized.length; i++) {
            normalized[i] = Experiment3CComposite.radiusNormalize(
                    radiusOnly[i], radii[i], 1);
        }
        assertEquals(0.0,
                Experiment3CComposite.coefficientOfVariation(normalized));
        assertTrue(Experiment3CComposite.coefficientOfVariation(
                2, 1, 1, 1, 1, 1) > 0);

        Path root = Path.of("measurements",
                "experiment-3c-axis-averaged-source");
        Assumptions.assumeTrue(Files.exists(root.resolve("report.md")),
                "Experiment 3C generated evidence is not present");
        for (String file : List.of(
                "input-validation.csv", "composite-detector-summary.csv",
                "composite-cycle-summary.csv",
                "composite-opposite-parity.csv",
                "composite-direction-class-summary.csv",
                "composite-cubic-groups.csv",
                "radius-normalization-summary.csv",
                "directional-isotropy-summary.csv",
                "metric-definition-comparison.csv")) {
            assertTrue(Files.size(root.resolve(file)) > 0, file);
        }
        assertTrue(Files.size(root.resolve(
                "charts/chart-validation.txt")) > 0);
    }

    @Test
    void experiment3dExactShellHasValidatedClosedCubicOrbits() {
        List<Experiment3DGeometry.Direction> directions =
                Experiment3DGeometry.directions();
        assertEquals(150, directions.size());
        assertEquals(Map.of(
                "ORBIT_25_0_0", 6L,
                "ORBIT_24_7_0", 24L,
                "ORBIT_20_15_0", 24L,
                "ORBIT_20_12_9", 48L,
                "ORBIT_16_15_12", 48L),
                directions.stream().collect(java.util.stream.Collectors
                        .groupingBy(
                                Experiment3DGeometry.Direction::orbitId,
                                java.util.stream.Collectors.counting())));
        Set<Vector3i> offsets = directions.stream()
                .map(Experiment3DGeometry.Direction::offset)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(150, offsets.size());
        Set<String> labels = new HashSet<>();
        for (Experiment3DGeometry.Direction direction : directions) {
            Vector3i offset = direction.offset();
            assertEquals(625, offset.lengthSquared());
            assertEquals(25.0, Math.sqrt(offset.lengthSquared()));
            assertTrue(labels.add(direction.label()));
            Experiment3DGeometry.Direction opposite =
                    Experiment3DGeometry.byOffset().get(
                            new Vector3i(offset).negate());
            assertNotNull(opposite);
            assertEquals(direction.oppositeLabel(), opposite.label());
            assertEquals(direction.orbitId(), opposite.orbitId());
        }
        Experiment3DGeometry.orbits().forEach(orbit -> {
            assertEquals(625, orbit.primitive().lengthSquared());
            assertEquals(orbit.expectedSize(),
                    Experiment3DGeometry.signedPermutations(
                            orbit.primitive()).size());
        });
        assertEquals(1.0, Experiment3DGeometry.cubicInvariantI4(
                new Vector3i(25, 0, 0)));
    }

    @Test
    void experiment3dRotationsAndCompositeStatisticsAreClosed() {
        Map<Vector3i, Experiment3DGeometry.Direction> shell =
                Experiment3DGeometry.byOffset();
        for (Experiment3BRotation.SourceAxis axis
                : Experiment3BRotation.SourceAxis.values()) {
            for (Experiment3DGeometry.Direction direction
                    : Experiment3DGeometry.directions()) {
                Vector3i canonical =
                        Experiment3BRotation.canonicalComponents(
                                direction.offset(), axis);
                assertTrue(shell.containsKey(canonical));
                assertEquals(direction.offset().lengthSquared(),
                        canonical.lengthSquared());
            }
        }
        double mean = Experiment3CComposite.arithmeticMean(1, 2, 3);
        double pooled = Experiment3CComposite.pooledRms(1, 2, 3);
        assertTrue(pooled >= mean);
        assertEquals((pooled - mean) / mean,
                (pooled - mean) / Math.max(Math.abs(mean), Math.ulp(1.0)));
        assertEquals(0.0,
                Experiment3AGeometry.evenDifference(2, 2));
        assertEquals(0.0,
                Experiment3AGeometry.oddResidual(2, -2));
    }

    @Test
    void experiment3dProjectsAndGeneratedEvidenceAreComplete()
            throws Exception {
        Set<Set<Vector3i>> placements = new HashSet<>();
        for (String axis : List.of("x", "y", "z")) {
            Wini ini = new Wini(new File(
                    "samples/experiment-3d-exact-radius-"
                            + axis + ".qfs"));
            List<Detector> detectors = DetectorConfiguration.load(
                    ini, new Vector3i(181, 181, 181));
            assertEquals(150, detectors.size());
            placements.add(detectors.stream().map(Detector::getNodeIndex)
                    .map(Vector3i::new).collect(
                            java.util.stream.Collectors.toSet()));
        }
        assertEquals(1, placements.size());
        Path root = Path.of("measurements",
                "experiment-3d-exact-radius-angular-structure");
        Assumptions.assumeTrue(Files.exists(root.resolve(
                "combined/report.md")),
                "Experiment 3D generated evidence is not present");
        for (String file : List.of(
                "detector-geometry.csv", "input-validation.csv",
                "safe-cycle-intersection.csv",
                "source-relative-rotation-map.csv",
                "source-relative-comparison.csv",
                "rotational-residuals.csv",
                "composite-detector-summary.csv",
                "composite-cycle-summary.csv",
                "composite-opposite-parity.csv",
                "cubic-orbit-summary.csv",
                "cubic-orbit-pairwise-comparison.csv",
                "angular-isotropy-summary.csv",
                "cubic-invariant-analysis.csv",
                "metric-definition-comparison.csv")) {
            Path combined = root.resolve("combined/" + file);
            Path shared = root.resolve(file);
            assertTrue((Files.exists(combined) && Files.size(combined) > 0)
                    || (Files.exists(shared) && Files.size(shared) > 0), file);
        }
        assertTrue(Files.size(root.resolve(
                "charts/chart-validation.txt")) > 0);
    }

    @Test
    void experiment3eExactCollinearShellsAreCompleteAndClosed() {
        List<Experiment3EGeometry.Direction> directions =
                Experiment3EGeometry.directions();
        assertEquals(234, directions.size());
        assertEquals(Map.of(
                "ORBIT_13_0_0", 18L,
                "ORBIT_12_5_0", 72L,
                "ORBIT_12_4_3", 144L),
                directions.stream().collect(java.util.stream.Collectors
                        .groupingBy(Experiment3EGeometry.Direction::orbitId,
                                java.util.stream.Collectors.counting())));
        assertEquals(78, directions.stream()
                .map(Experiment3EGeometry.Direction::baseDirectionLabel)
                .distinct().count());
        Map<String, Experiment3EGeometry.Direction> byLabel =
                directions.stream().collect(java.util.stream.Collectors.toMap(
                        Experiment3EGeometry.Direction::label,
                        direction -> direction));
        for (Experiment3EGeometry.Direction direction : directions) {
            assertEquals(new Vector3i(direction.baseOffset())
                            .mul(direction.scaleFactor()),
                    direction.offset());
            assertEquals(169 * direction.scaleFactor()
                            * direction.scaleFactor(),
                    direction.offset().lengthSquared());
            assertNotNull(byLabel.get(direction.oppositeLabel()));
            assertEquals(direction.orbitId(),
                    byLabel.get(direction.oppositeLabel()).orbitId());
            assertEquals(Experiment3EGeometry.cubicInvariantI4(
                            direction.baseOffset()),
                    Experiment3EGeometry.cubicInvariantI4(
                            direction.offset()), 1e-15);
        }
        assertEquals(List.of(6, 24, 48),
                Experiment3EGeometry.orbits().stream()
                        .map(Experiment3EGeometry.Orbit::expectedSize).toList());
    }

    @Test
    void experiment3eRotationsStatisticsAndTrendClassificationAreSound() {
        Set<Vector3i> bases = Experiment3EGeometry.directions().stream()
                .filter(direction -> direction.scaleFactor() == 1)
                .map(Experiment3EGeometry.Direction::baseOffset)
                .collect(java.util.stream.Collectors.toSet());
        for (Experiment3BRotation.SourceAxis axis
                : Experiment3BRotation.SourceAxis.values()) {
            for (Vector3i base : bases) {
                assertTrue(bases.contains(
                        Experiment3BRotation.canonicalComponents(base, axis)));
            }
        }
        assertEquals(Math.sqrt(14.0 / 3.0),
                Experiment3EAnalysis.pooledRms(1, 2, 3));
        assertEquals(2.0, Experiment3EAnalysis.mean(1, 2, 3));
        assertEquals(0.0,
                Experiment3EAnalysis.directionalCv(2, 2, 2));
        assertEquals(0.0,
                Experiment3EAnalysis.directionalSpread(2, 2, 2));
        assertEquals("decreasing",
                Experiment3EAnalysis.classifyTrend(3, 2, 1));
        assertEquals("increasing",
                Experiment3EAnalysis.classifyTrend(1, 2, 3));
        assertEquals("approximately stable",
                Experiment3EAnalysis.classifyTrend(2, 2, 2));
        assertEquals("non-monotonic",
                Experiment3EAnalysis.classifyTrend(1, 3, 2));
        assertThrows(IllegalArgumentException.class,
                () -> Experiment3EAnalysis.mean(Double.NaN));
    }

    @Test
    void experiment3eProjectsAndEvidenceAreComplete() throws Exception {
        Set<Set<Vector3i>> placements = new HashSet<>();
        for (String axis : List.of("x", "y", "z")) {
            Wini ini = new Wini(new File(
                    "samples/experiment-3e-angular-evolution-"
                            + axis + ".qfs"));
            List<Detector> detectors = DetectorConfiguration.load(
                    ini, new Vector3i(211, 211, 211));
            assertEquals(234, detectors.size());
            placements.add(detectors.stream().map(Detector::getNodeIndex)
                    .map(Vector3i::new).collect(
                            java.util.stream.Collectors.toSet()));
        }
        assertEquals(1, placements.size());
        Path root = Path.of("measurements",
                "experiment-3e-angular-evolution-with-radius");
        Assumptions.assumeTrue(Files.exists(root.resolve(
                "combined/report.md")),
                "Experiment 3E generated evidence is not present");
        for (String file : List.of(
                "detector-geometry.csv", "input-validation.csv",
                "safe-cycle-intersection.csv",
                "source-relative-rotation-map.csv",
                "rotational-residuals.csv",
                "composite-detector-summary.csv",
                "composite-cycle-summary.csv",
                "shell-angular-summary.csv",
                "normalized-angular-response.csv",
                "orbit-summary-by-radius.csv",
                "orbit-pairwise-evolution.csv",
                "opposite-parity-by-radius.csv",
                "cubic-invariant-by-radius.csv",
                "cross-radius-pattern-comparison.csv",
                "same-direction-radial-comparison.csv",
                "metric-definition-comparison.csv",
                "runtime-memory-summary.csv")) {
            assertTrue(Files.size(root.resolve("combined/" + file)) > 0,
                    file);
        }
        assertTrue(Files.size(root.resolve(
                "charts/chart-validation.txt")) > 0);
    }

    private static void usingAxis(
            Experiment3BRotation.SourceAxis axis, Vector3d direction) {
        assertEquals(0.0,
                Experiment3BRotation.sourcePolarDegrees(direction, axis));
        assertEquals(180.0, Experiment3BRotation.sourcePolarDegrees(
                new Vector3d(direction).negate(), axis));
    }

    private static Wini detectorIni(int size) {
        Wini ini = new Wini();
        ini.put("Detectors", "Size", size);
        return ini;
    }

    private static void putDetector(Wini ini, int index, String name,
                                    int x, int y, int z) {
        String section = "Detector" + index;
        ini.put(section, "name", name);
        ini.put(section, "nodeIndex.x", x);
        ini.put(section, "nodeIndex.y", y);
        ini.put(section, "nodeIndex.z", z);
    }

    private static Node symmetricNode() {
        Node center = node(0, 0, 0);
        center.setNeighbor(Neighbor.RIGHT, node(1, 0, 0));
        center.setNeighbor(Neighbor.LEFT, node(-1, 0, 0));
        center.setNeighbor(Neighbor.TOP, node(0, 1, 0));
        center.setNeighbor(Neighbor.BOTTOM, node(0, -1, 0));
        return center;
    }

    private static Node node(double x, double y, double z) {
        return new Node(null, null, new Vector3d(x, y, z),
                new Vector3d(), 1.0);
    }

    private static void prepareSingleNodeLattice(Node node) {
        Project.getInstance().setDimension(1, 1, 1, false);
        node.setIndex(0, 0, 0);
        Lattice.getInstance().setNodeArray(new Object[]{node});
    }

    private static Vector3d[] copy(Vector3d[] values) {
        Vector3d[] result = new Vector3d[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new Vector3d(values[i]);
        }
        return result;
    }

    private static void assertVectorArrayEquals(
            Vector3d[] expected, Vector3d[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }
}
