package com.example.shortsgesturecontrol;

/** Shared, allocation-free two-bone skinning used by Android and the motion preview. */
public final class CatRig {
    public static final int COLS = 8, ROWS = 20;
    public static final double STRIDE = 60, STANCE = .64, PAW_LIFT = 26;
    public static final double GROUND = 468;
    // Keep the artwork's bind joints, but repose the front pair so both paws
    // stand under their shoulders instead of leaving the near paw reaching ahead.
    public static final Leg FRONT_NEAR = new Leg("front_near",104,315,120,153,172,331,179,396,146,453,0,24,6);
    public static final Leg FRONT_FAR = new Leg("front_far",165,315,90,153,200,331,220,394,203,453,.5,-4,0);
    // Seat the upper thigh inside the painted haunch instead of exposing its
    // closed attachment cap to the right of the torso silhouette.
    public static final Leg REAR_NEAR = new Leg("rear_near",312,300,116,168,342,317,378,388,376,453,.25);
    public static final Leg REAR_FAR = new Leg("rear_far",245,305,117,163,277,320,306,386,287,453,.75);
    public static final Leg[] LEGS = { REAR_FAR, FRONT_FAR, REAR_NEAR, FRONT_NEAR };

    /** One shared rigid-body pose. Artwork is not stretched to make it breathe. */
    public static final class Pose {
        public double y, angle, headAngle, tailAngle;
        private double ca=1,sa=0;
        public void sample(double cycle,double activity,double seconds) {
            double phase=2*Math.PI*cycle;
            y=activity*(5.0+1.7*(1-Math.cos(2*phase)));
            angle=activity*.024*Math.sin(phase-.35);
            ca=Math.cos(angle);sa=Math.sin(angle);
            // Broader, coordinated neck/tail arcs, not independent twitching.
            headAngle=-angle*.65+activity*(.030*Math.sin(phase-.25)+.012*Math.sin(2*phase-.4))
                +(1-activity)*.012*Math.sin(seconds*.75);
            tailAngle=-angle*.5+activity*.075*Math.sin(phase-.8)
                +(1-activity)*.022*Math.sin(seconds*.9);
        }
        public double x(double x,double y) { return 285+(x-285)*ca-(y-333)*sa; }
        public double y(double x,double y) { return 333+this.y+(x-285)*sa+(y-333)*ca; }
    }

    public static final class Leg {
        public final String name;
        public final double x,y,width,height,hx,hy,kx,ky,fx,fy,offset;
        /** Neutral-pose offsets, separate from the source artwork's bind joints. */
        public final double restFootShift,shoulderRise,standingReach,boneLength;
        public final float[] vertices = new float[(COLS+1)*(ROWS+1)*2];
        private final double upper,lower,bend;
        Leg(String name,double x,double y,double width,double height,
            double hx,double hy,double kx,double ky,double fx,double fy,double offset) {
            this(name,x,y,width,height,hx,hy,kx,ky,fx,fy,offset,0,0);
        }
        Leg(String name,double x,double y,double width,double height,
            double hx,double hy,double kx,double ky,double fx,double fy,double offset,
            double restFootShift,double shoulderRise) {
            this.name=name; this.x=x; this.y=y; this.width=width; this.height=height;
            this.hx=hx; this.hy=hy; this.kx=kx; this.ky=ky; this.fx=fx; this.fy=fy; this.offset=offset;
            this.restFootShift=restFootShift;this.shoulderRise=shoulderRise;
            upper=Math.hypot(kx-hx,ky-hy); lower=Math.hypot(fx-kx,fy-ky);
            bend=Math.signum((fx-hx)*(ky-hy)-(fy-hy)*(kx-hx));
            boneLength=upper+lower;
            double standingHeight=fy-shoulderY();
            standingReach=Math.sqrt(Math.max(0,boneLength*boneLength-standingHeight*standingHeight))-3;
        }
        public double restFootX() { return fx+restFootShift; }
        public double shoulderY() { return hy-shoulderRise; }
    }

    /** Left-facing local foot offset. During stance dx/dcycle == STRIDE:
     * the root moves left by STRIDE, so the world-space paw remains fixed. */
    public static double footX(double cycle) {
        double p=cycle-Math.floor(cycle), span=STRIDE*STANCE;
        if(p<STANCE) return -span/2+STRIDE*p;
        double t=(p-STANCE)/(1-STANCE);
        // Match stance velocity at both ends; no horizontal jerk at touchdown.
        double h=t*t*t*(10+t*(-15+6*t));
        double v=t-10*t*t*t+15*t*t*t*t-6*t*t*t*t*t;
        return span/2-span*h+STRIDE*(1-STANCE)*v;
    }
    public static double footLift(double cycle) {
        double p=cycle-Math.floor(cycle);
        if(p<STANCE) return 0;
        return recoveryLift((p-STANCE)/(1-STANCE));
    }
    public static double recoveryLift(double t) {
        t=Math.max(0,Math.min(1,t));
        double arch=t*(1-t);
        // A broader lifted interval, still zero velocity/acceleration at contact.
        return PAW_LIFT*64*arch*arch*arch;
    }
    public static double bodyY(double cycle,double blend) {
        return -1.2*blend*(1-Math.cos(4*Math.PI*cycle));
    }
    public static void skin(Leg l,double cycle,double blend,double bodyY) {
        Pose pose=new Pose();
        pose.y=bodyY;
        skin(l,pose,footX(cycle+l.offset)*blend,footLift(cycle+l.offset)*blend);
    }
    public static void skin(Leg l,Pose pose,double footX,double lift) {
        double fx=l.restFootX()+footX,fy=l.fy-lift;
        double hx=pose.x(l.hx,l.shoulderY()),hy=pose.y(l.hx,l.shoulderY());
        double dx=fx-hx,dy=fy-hy,dist=Math.hypot(dx,dy);
        double d=Math.max(Math.abs(l.upper-l.lower)+.001,Math.min(dist,l.upper+l.lower-.001));
        double along=(l.upper*l.upper-l.lower*l.lower+d*d)/(2*d);
        double across=Math.sqrt(Math.max(0,l.upper*l.upper-along*along))*l.bend;
        double ux=dx/dist,uy=dy/dist;
        double kx=hx+ux*along-uy*across,ky=hy+uy*along+ux*across;
        double a=Math.atan2(ky-hy,kx-hx)-Math.atan2(l.ky-l.hy,l.kx-l.hx);
        double b=Math.atan2(fy-ky,fx-kx)-Math.atan2(l.fy-l.ky,l.fx-l.kx);
        double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
        int index=0;
        for(int row=0;row<=ROWS;row++) {
            double y=l.y+l.height*row/ROWS;
            double lowerWeight=smooth((y-l.ky+12)/24);
            double pawWeight=smooth((y-l.fy+16)/18);
            for(int col=0;col<=COLS;col++) {
                double x=l.x+l.width*col/COLS;
                double ax=hx+(x-l.hx)*ca-(y-l.hy)*sa;
                double ay=hy+(x-l.hx)*sa+(y-l.hy)*ca;
                double bx=kx+(x-l.kx)*cb-(y-l.ky)*sb;
                double by=ky+(x-l.kx)*sb+(y-l.ky)*cb;
                double vx=ax+(bx-ax)*lowerWeight,vy=ay+(by-ay)*lowerWeight;
                // The attachment patch belongs to the torso, not to a rotating
                // closed limb cap. Only the exposed limb articulates below it.
                double rootWeight=1-smooth((y-l.hy-3)/25);
                vx+=(pose.x(x,y-l.shoulderRise)-vx)*rootWeight;
                vy+=(pose.y(x,y-l.shoulderRise)-vy)*rootWeight;
                // Third bone keeps the paw level through contact and recovery.
                vx+=(fx+x-l.fx-vx)*pawWeight;
                vy+=(fy+y-l.fy-vy)*pawWeight;
                l.vertices[index++]=(float)vx; l.vertices[index++]=(float)vy;
            }
        }
    }
    private static double smooth(double t) {
        t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t);
    }
    public static double ease(double t) {
        t=Math.max(0,Math.min(1,t));return t*t*t*(10+t*(-15+6*t));
    }
    private CatRig() {}
}
