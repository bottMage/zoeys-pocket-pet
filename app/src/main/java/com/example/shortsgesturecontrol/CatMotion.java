package com.example.shortsgesturecontrol;

/** Distance-driven roaming, with persistent world contacts and timed recoveries. */
public final class CatMotion {
    public double position=.5,blend=0,cycle=0;
    public boolean facingRight=false;
    public final CatRig.Pose pose=new CatRig.Pose();
    public final Foot[] feet=new Foot[CatRig.LEGS.length];
    private int direction=-1;
    private boolean walking=false;
    private double remaining=1.8,speed=0,distance=0,seconds=0;
    private static final double SPEED=42,ACCELERATION=110;
    private static final int[] STEP_ORDER={2,3,0,1};

    public static final class Foot {
        public double x=0,lift=0;
        public boolean grounded=true;
        private double contact=0,nextLift=0,age=0,duration=0,from=0,to=0;
    }

    public CatMotion() { for(int i=0;i<feet.length;i++) feet[i]=new Foot(); }

    public void advance(double dt,double travelRange,double artScale,boolean actionActive) {
        if(!Double.isFinite(dt)||dt<=0) return;
        dt=Math.min(.12,dt);
        int steps=(int)Math.ceil(dt*120);
        for(int i=0;i<steps;i++) step(dt/steps,travelRange,artScale,actionActive);
    }

    private void startWalk() {
        walking=true;remaining=4.2;distance=0;
        if(position<=.001) direction=1;
        if(position>=.999) direction=-1;
        facingRight=direction>0;
        // Near hind -> near fore -> far hind -> far fore. Begin with the
        // paw nearest its reach limit, instead of resetting the standing pose.
        int lead=0;
        for(int i=1;i<4;i++) if(urgency(STEP_ORDER[i])>urgency(STEP_ORDER[lead])) lead=i;
        for(int i=0;i<4;i++) {
            int index=STEP_ORDER[(lead+i)%4];
            Foot f=feet[index];
            f.contact=f.x;f.nextLift=Math.min(i*CatRig.STRIDE/4,Math.max(0,-urgency(index)));
            f.age=0;f.grounded=true;f.lift=0;
        }
    }

    private double urgency(int index) {
        CatRig.Leg l=CatRig.LEGS[index];
        double length=Math.hypot(l.kx-l.hx,l.ky-l.hy)+Math.hypot(l.fx-l.kx,l.fy-l.ky);
        double height=l.fy-l.shoulderY()-3;
        double reach=Math.sqrt(Math.max(0,length*length-height*height))-(l.restFootX()-l.hx)-3;
        return feet[index].x-reach;
    }

    private void step(double dt,double range,double scale,boolean action) {
        seconds+=dt;
        boolean valid=Double.isFinite(range)&&Double.isFinite(scale)&&range>0&&scale>0;
        boolean recovering=false;
        for(Foot f:feet) recovering|=!f.grounded;
        if(!action && valid) {
            remaining-=dt;
            if(walking && remaining<=0) {walking=false;remaining=1.1;}
            else if(!walking && remaining<=0 && speed<.001 && !recovering) startWalk();
        }
        double target=walking&&!action&&valid?SPEED:0;
        double edge=valid?(direction<0?position:1-position)*range/scale:0;
        if(target>0) target=Math.min(target,Math.sqrt(Math.max(0,2*ACCELERATION*edge))*.85);
        double previousSpeed=speed;
        speed+=Math.max(-ACCELERATION*dt,Math.min(ACCELERATION*dt,target-speed));
        double moved=valid?Math.min(edge,(previousSpeed+speed)*.5*dt):0;
        if(moved>0) {
            position=Math.max(0,Math.min(1,position+direction*moved*scale/range));
            distance+=moved;cycle+=moved/CatRig.STRIDE;
        }
        if(valid && walking && edge-moved<.04) { walking=false;remaining=.9; }
        if(!valid) speed=0;
        // A recovery finishes even when an action or a stop pauses travel.
        for(int index=0;index<feet.length;index++) {
            Foot f=feet[index];CatRig.Leg l=CatRig.LEGS[index];
            double local=f.contact+distance;
            // The more upright front stance has less horizontal reach. Finish
            // with a real recovery step, not a stretched joint or a dragged paw,
            // if braking/an action would leave it outside its standing reach.
            boolean front=l==CatRig.FRONT_NEAR || l==CatRig.FRONT_FAR;
            boolean frontLimit=front && Math.abs(l.restFootX()+local-l.hx)>=l.standingReach;
            boolean normalStep=walking && !action && moved>0 && distance>=f.nextLift;
            if(f.grounded && (normalStep || frontLimit)) {
                f.grounded=false;f.age=0;f.from=f.contact;
                f.duration=Math.max(.24,Math.min(.42,CatRig.STRIDE*(1-CatRig.STANCE)/Math.max(30,speed)));
                double recoverySpeed=front?240:260;
                f.duration=Math.max(f.duration,Math.min(.65,1.875*Math.max(0,local+CatRig.STRIDE*CatRig.STANCE*.5)/(recoverySpeed-1.875*speed)));
                double accelerating=Math.min(f.duration,Math.max(0,(SPEED-speed)/ACCELERATION));
                double forecast=speed*f.duration+ACCELERATION*(f.duration*accelerating-.5*accelerating*accelerating);
                boolean settling=target==0;
                if(settling) {
                    double braking=Math.min(f.duration,speed/ACCELERATION);
                    forecast=speed*braking-.5*ACCELERATION*braking*braking;
                }
                double predicted=distance+Math.min(edge-moved,forecast);
                f.to=(settling?0:-CatRig.STRIDE*CatRig.STANCE*.5)-predicted;
                f.nextLift=settling?predicted+CatRig.STRIDE/2:f.nextLift+CatRig.STRIDE;
            }
            if(!f.grounded) {
                f.age+=dt;
                double t=Math.min(1,f.age/f.duration);
                double h=CatRig.ease(t),s=Math.sin(Math.PI*t);
                f.x=f.from+(f.to-f.from)*h+distance;
                f.lift=15*s*s*s*s;
                if(t>=1) { f.grounded=true;f.contact=f.to;f.lift=0; }
            } else { f.x=f.contact+distance;f.lift=0; }
        }
        double activity=Math.max(speed/SPEED,recovering?.35:0);
        blend+=(activity-blend)*(1-Math.exp(-dt*9));
        if(blend<.00001) blend=0;
        pose.sample(cycle,blend,seconds);
    }
}
