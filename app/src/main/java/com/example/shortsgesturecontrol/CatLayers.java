package com.example.shortsgesturecontrol;

/** Shared compositing plan; independent of movement, clocks and save state. */
public final class CatLayers {
    // CatRig.LEGS: far hind, far fore, near hind, near fore.
    public static final int[] FAR_LEGS={0,1},NEAR_LEGS={2,3};
    public static final double TAIL_X=320,TAIL_Y=95,TAIL_WIDTH=175,TAIL_HEIGHT=203;
    public static final double TAIL_PIVOT_X=348,TAIL_PIVOT_Y=270;

    /** Open the hidden attachment cap; retain all art below the joint. */
    public static int attachmentAlpha(String part,double u,double v) {
        if(part.equals("tail")) {
            // The closed crescent end is an authoring cap, not a contour across
            // the rump. Open only that proximal end; the free tail stays opaque.
            double root=CatRig.ease((u-.02)/.32)*(1-CatRig.ease((v-.80)/.12));
            double free=CatRig.ease((u-.42)/.08);
            double proximal=CatRig.ease((v-.58)/.12);
            return (int)Math.round(255*(1+(root+(1-root)*free-1)*proximal));
        }
        CatRig.Leg leg=part.equals("rear_near")?CatRig.REAR_NEAR:
            part.equals("front_near")?CatRig.FRONT_NEAR:null;
        if(leg==null) return 255;
        double y=leg.y+v*leg.height;
        return (int)Math.round(255*CatRig.ease((y-(leg.hy-4))/24));
    }
    private CatLayers() {}
}
