package dev.reme.rpmotion;
/** Pure software intent. Does NOT create hardware or fake physical pose changes. */
public record GaitCommand(String jointId, double targetDegrees, double maximumSpeedDegPerSecond, long expiryTick) {
    public GaitCommand {
        if(jointId==null || !jointId.matches("(LF|RF|LR|RR)\\.(HAA|HFE|KFE)"))
            throw new IllegalArgumentException("Unknown real bearing joint identifier");
        if(!Double.isFinite(targetDegrees)||!Double.isFinite(maximumSpeedDegPerSecond)
            ||maximumSpeedDegPerSecond<0||maximumSpeedDegPerSecond>90||expiryTick<0)
            throw new IllegalArgumentException("Unsafe joint command");
    }
    public boolean fresh(long currentTick){return currentTick<=expiryTick;}
}
