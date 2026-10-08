package com.example.shortsgesturecontrol;

/** Persistent contacts and timed joint recoveries, parameterized by anatomy. */
public final class PetMotion {
    public final PetRig rig;
    public final PetRig.Pose pose;
    public final Foot[] feet={new Foot(),new Foot(),new Foot(),new Foot()};
    public double position=.5,blend=0,cycle=0;
    public boolean facingRight=false;
    private int direction=-1;
    private boolean walking=false;
    private double remaining=1.2,speed=0,distance=0,seconds=0;
    private static final double ACCELERATION=210,PAW_SPEED=410;
    public static final class Foot {
        public double x,lift;
        public boolean grounded=true;
        private double contact,nextLift,age,duration,from,to;
    }
    public PetMotion(PetRig rig){this.rig=rig;pose=new PetRig.Pose(rig);}
    public void advance(double dt,double range,double scale,boolean action) {
        if(!Double.isFinite(dt)||dt<=0)return;
        dt=Math.min(.12,dt);int steps=(int)Math.ceil(dt*120);
        for(int i=0;i<steps;i++)step(dt/steps,range,scale,action);
    }
    private double reach(int index) {
        PetRig.Leg l=rig.legs[index];
        double hx=pose.x(l.hx,l.hy),height=l.fy-pose.y(l.hx,l.hy);
        return Math.sqrt(Math.max(0,l.length*l.length-height*height))-(l.restX-hx)-2.5;
    }
    private void startWalk() {
        walking=true;remaining=4.2;distance=0;
        if(position<=.001)direction=1;if(position>=.999)direction=-1;
        facingRight=direction>0;
        int lead=0;for(int i=1;i<4;i++)if(feet[i].x-reach(i)>feet[lead].x-reach(lead))lead=i;
        double phase=rig.legs[lead].phase;
        for(int i=0;i<4;i++) {
            Foot f=feet[i];double gap=rig.legs[i].phase-phase;if(gap<0)gap+=1;
            f.contact=f.x;f.nextLift=Math.min(gap*rig.stride,Math.max(0,reach(i)-f.x));
            f.age=0;f.grounded=true;f.lift=0;
        }
    }
    private void step(double dt,double range,double scale,boolean action) {
        seconds+=dt;
        boolean valid=Double.isFinite(range)&&Double.isFinite(scale)&&range>0&&scale>0;
        int recoveringCount=0;for(Foot f:feet)if(!f.grounded)recoveringCount++;
        boolean recovering=recoveringCount>0;
        if(!action&&valid) {
            remaining-=dt;
            if(walking&&remaining<=0){walking=false;remaining=.7;}
            else if(!walking&&remaining<=0&&speed<.001&&!recovering)startWalk();
        }
        double target=walking&&!action&&valid?rig.speed:0;
        double edge=valid?(direction<0?position:1-position)*range/scale:0;
        if(target>0)target=Math.min(target,Math.sqrt(Math.max(0,2*ACCELERATION*edge))*.85);
        double previous=speed;speed+=Math.max(-ACCELERATION*dt,Math.min(ACCELERATION*dt,target-speed));
        double moved=valid?Math.min(edge,(previous+speed)*.5*dt):0;
        // If three paws are recovering, the remaining support cannot be
        // pulled beyond its joint reach while waiting for a landing.
        double supportedMove=moved;
        for(int i=0;i<4;i++)if(feet[i].grounded)
            supportedMove=Math.min(supportedMove,Math.max(0,reach(i)+1.5-feet[i].x));
        if(supportedMove<moved){moved=supportedMove;speed=Math.min(speed,moved/dt);}
        if(moved>0){position=Math.max(0,Math.min(1,position+direction*moved*scale/range));distance+=moved;cycle+=moved/rig.stride;}
        if(valid&&walking&&edge-moved<.04){walking=false;remaining=.65;}
        if(!valid)speed=0;
        for(int i=0;i<4;i++) {
            Foot f=feet[i];PetRig.Leg l=rig.legs[i];double local=f.contact+distance;
            double hx=pose.x(l.hx,l.hy),height=l.fy-pose.y(l.hx,l.hy);
            double available=Math.sqrt(Math.max(0,l.length*l.length-height*height))-2.5;
            boolean limit=Math.abs(l.restX+local-hx)>=available;
            boolean normal=walking&&!action&&moved>0&&distance>=f.nextLift;
            // A fourth recovery waits for a supporting paw to land. In
            // particular, the dog/rabbit paired gait must not float en masse.
            if(f.grounded&&(normal||limit)&&recoveringCount<3) {
                f.grounded=false;f.age=0;f.from=f.contact;
                recoveringCount++;
                double minDuration=rig.kind.equals("hamster")?.19:.28;
                f.duration=Math.max(minDuration,Math.min(.40,rig.stride*(1-rig.stance)/Math.max(45,speed)));
                double cap=PAW_SPEED-50;
                f.duration=Math.max(f.duration,Math.min(.65,1.875*Math.max(0,local+rig.stride*rig.stance*.5)/(cap-1.875*speed)));
                double acceleration=Math.min(f.duration,Math.max(0,(rig.speed-speed)/ACCELERATION));
                double forecast=speed*f.duration+ACCELERATION*(f.duration*acceleration-.5*acceleration*acceleration);
                boolean settle=target==0;
                if(settle){double brake=Math.min(f.duration,speed/ACCELERATION);forecast=speed*brake-.5*ACCELERATION*brake*brake;}
                double predicted=distance+Math.min(edge-moved,forecast);
                f.to=(settle?0:-rig.stride*rig.stance*.5)-predicted;
                // A resize or action may stop root travel during recovery.
                // Do not promise a touchdown that is reachable only if the
                // forecast travel actually happens. Reserve the upright
                // shoulder's worst pitch displacement, without moving a
                // planted contact or lengthening either bone.
                double heightAtRest=l.fy-l.hy;
                double uprightReach=Math.sqrt(Math.max(0,l.length*l.length-heightAtRest*heightAtRest));
                double pitchReserve=Math.abs(l.hy-rig.cy)*rig.pitch+Math.abs(l.hx-rig.cx)*rig.pitch*2;
                double forwardReach=Math.max(0,uprightReach+(l.restX-l.hx)-pitchReserve-3);
                f.to=Math.max(f.to,-forwardReach-distance);
                f.nextLift=settle?predicted+rig.stride/2:f.nextLift+rig.stride;
            }
            if(!f.grounded) {
                f.age+=dt;double t=Math.min(1,f.age/f.duration);
                f.x=f.from+(f.to-f.from)*CatRig.ease(t)+distance;f.lift=rig.footLift(t);
                if(t>=1){f.grounded=true;f.contact=f.to;f.lift=0;recoveringCount--;}
            } else {f.x=f.contact+distance;f.lift=0;}
        }
        double activity=Math.max(speed/rig.speed,recovering?.35:0);
        blend+=(activity-blend)*(1-Math.exp(-dt*9));if(blend<.00001)blend=0;
        pose.sample(rig,cycle,blend,seconds);
    }
}
