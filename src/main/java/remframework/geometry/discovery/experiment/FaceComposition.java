package remframework.geometry.discovery.experiment;

/** Coordinate-free face-composition conditions attached to an experiment request. */
public record FaceComposition(FaceCompositionMode mode, Integer commonFaceEdgeCount) {
    public FaceComposition {
        mode = mode == null ? FaceCompositionMode.DISCOVER : mode;
        if (mode == FaceCompositionMode.UNIFORM_FACE_SIZE
                && (commonFaceEdgeCount == null || commonFaceEdgeCount < 3)) {
            throw new IllegalArgumentException(
                    "commonFaceEdgeCount must be an integer >= 3 in UNIFORM_FACE_SIZE mode.");
        }
        if (mode == FaceCompositionMode.DISCOVER) commonFaceEdgeCount = null;
    }

    public static FaceComposition discover() {
        return new FaceComposition(FaceCompositionMode.DISCOVER, null);
    }

    public static FaceComposition uniform(int commonFaceEdgeCount) {
        return new FaceComposition(FaceCompositionMode.UNIFORM_FACE_SIZE, commonFaceEdgeCount);
    }
}
