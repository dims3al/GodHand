package xyz.dimseal.godHand.hand.skeleton;

import xyz.dimseal.godHand.model.ModelPoint;

import java.util.List;
import java.util.Map;

/** One evaluated articulated hand frame, still in hand-local coordinates. */
public record SkeletalFrame(
        List<ModelPoint> surfacePoints,
        Map<HandDigit, List<ModelPoint>> digitSurfacePoints,
        List<ModelPoint> bonePoints,
        List<ModelPoint> jointPoints
) {
}
