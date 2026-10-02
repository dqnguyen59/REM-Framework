package remframework.geometry.discovery;

public record StagnationStatus(StagnationClass classification,String reason,
        double relativeObjectiveImprovement,int windowSize) {
    public static StagnationStatus progressing(){return new StagnationStatus(
            StagnationClass.PROGRESSING,"Insufficient samples for sliding-window classification.",0,0);}
}
