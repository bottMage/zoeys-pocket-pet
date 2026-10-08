package com.example.shortsgesturecontrol;

/** Shared, allocation-free two-bone skinning used by Android and the motion preview. */
public final class CatRig {
    public static final int COLS = 8, ROWS = 20;
    public static final double STRIDE = 48, STANCE = .68;
    public static final double GROUND = 468;
    public static final Leg FRONT_NEAR = new Leg("front_near",70,315,120,153,138,331,145,396,112,453,0);
    public static final Leg FRONT_FAR = new Leg("front_far",165,315,90,153,200,331,220,394,203,453,.5);
    public static final Leg REAR_NEAR = new Leg("rear_near",330,300,116,168,360,317,396,388,394,453,.25);
    public static final Leg REAR_FAR = new Leg("rear_far",245,305,117,163,277,320,306,386,287,453,.75);
    public static final Leg[] LEGS = { REAR_FAR, FRONT_FAR, REAR_NEAR, FRONT_NEAR };

    public static final class Leg {
        public final String name;
        public final double x,y,width,height,hx,hy,kx,ky,fx,fy,offset;
        public final float[] vertices = new float[(COLS+1)*(ROWS+1)*2];
        private final double upper,lower,bend;
        Leg(String name,double x,double y,double width,double height,
            double hx,double hy,double kx,double ky,double fx,double fy,double offset) {
            this.name=name; this.x=x; this.y=y; this.width=width; this.height=height;
            this.hx=hx; this.hy=hy; this.kx=kx; this.ky=ky; this.fx=fx; this.fy=fy; this.offset=offset;
            upper=Math.hypot(kx-hx,ky-hy); lower=Math.hypot(fx-kx,fy-ky);
            bend=Math.signum((fx-hx)*(ky-hy)-(fy-hy)*(kx-hx));
        }
    }

    /** Left-facing local foot offset. During stance dx/dcycle == STRIDE:
     * the root moves left by STRIDE, so the world-space paw remains fixed. */
    public static double footX(double cycle) {
        double p=cycle-Math.floor(cycle), span=STRIDE*STANCE;
        if(p<STANCE) return -span/2+STRIDE*p;
        double t=(p-STANCE)/(1-STANCE);
        // Match stance velocity at both ends; no horizontal jerk at touchdown.
        double h=3*t*t-2*t*t*t;
        return span/2-span*h+STRIDE*(1-STANCE)*t*(1-t)*(1-2*t);
    }
    public static double footLift(double cycle) {
        double p=cycle-Math.floor(cycle);
        if(p<STANCE) return 0;
        double t=(p-STANCE)/(1-STANCE), s=Math.sin(Math.PI*t);
        return 15*s*s; // zero vertical speed on lift-off and touchdown
    }
    public static double bodyY(double cycle,double blend) {
        return -1.2*blend*(1-Math.cos(4*Math.PI*cycle));
    }
    public static void skin(Leg l,double cycle,double blend,double bodyY) {
        double fx=l.fx+footX(cycle+l.offset)*blend;
        double fy=l.fy-footLift(cycle+l.offset)*blend;
        double hx=l.hx, hy=l.hy+bodyY;
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
    private CatRig() {}
}
