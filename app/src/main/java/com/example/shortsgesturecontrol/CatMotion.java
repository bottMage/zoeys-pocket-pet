package com.example.shortsgesturecontrol;

/** Roaming state shared by the game and the long-running motion regression. */
public final class CatMotion {
    public float position=.5f, blend=0;
    public double cycle=0;
    public boolean facingRight=false;
    private int direction=-1;
    private boolean walking=false;
    private double remaining=1.8, walkAge=0;

    public void advance(double dt,double travelRange,double artScale,boolean actionActive) {
        dt=Math.max(0,Math.min(.12,dt));
        if(!actionActive) {
            remaining-=dt;
            if(remaining<=0) {
                walking=!walking;
                remaining=walking?4.2:1.1;
                walkAge=0;
                if(walking && position<=.001) direction=1;
                if(walking && position>=.999) direction=-1;
            }
        }
        double ramp=walking&&!actionActive?Math.min(1,Math.min(walkAge/.45,remaining/.65)):0;
        ramp=Math.max(0,ramp);
        double target=ramp*ramp*(3-2*ramp);
        blend+=(float)((target-blend)*(1-Math.exp(-dt*12)));
        if(blend<.001) blend=0;
        if(!walking || actionActive || travelRange<=0 || artScale<=0) return;
        walkAge+=dt;
        float previous=position;
        double distance=dt*42*artScale*blend;
        position=(float)Math.max(0,Math.min(1,position+direction*distance/travelRange));
        double moved=Math.abs(position-previous)*travelRange;
        cycle+=moved/artScale/CatRig.STRIDE;
        if(moved>0) facingRight=direction>0;
        // A zero-speed start at an edge is NOT another collision. Only stop
        // after moving toward and reaching the destination edge.
        if(moved>0 && ((direction<0 && position<=0)||(direction>0 && position>=1))) {
            walking=false;
            remaining=.9;
            direction=-direction;
        }
    }
}
