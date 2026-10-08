import com.example.shortsgesturecontrol.CatMotion;
import com.example.shortsgesturecontrol.CatRig;

/** Production pose/contact regression, including starts, braking and actions. */
public class CatGaitCheck {
    static double world(CatMotion m,int i,double lane,double scale) {
        double x=CatRig.LEGS[i].restFootX()+m.feet[i].x-256;
        return m.position*lane/scale+(m.facingRight?-x:x);
    }
    static void checkRun(int fps,double lane) {
        CatMotion m=new CatMotion();double scale=.6;
        double maxSlip=0,maxReach=0,maxJump=0;
        int contacts=0;String jumpAt="",reachAt="";
        double[] previous=new double[4],previousX=new double[4],previousLift=new double[4];
        boolean[] planted=new boolean[4];
        for(int frame=0;frame<fps*40;frame++) {
            boolean facing=m.facingRight;
            for(int i=0;i<4;i++) {
                previous[i]=world(m,i,lane,scale);planted[i]=m.feet[i].grounded;
                previousX[i]=m.feet[i].x;previousLift[i]=m.feet[i].lift;
            }
            double t=(double)frame/fps;
            m.advance(1.0/fps,lane,scale,(t>8.15&&t<9.8)||(t>23.45&&t<25));
            for(int i=0;i<4;i++) {
                CatMotion.Foot foot=m.feet[i];CatRig.Leg l=CatRig.LEGS[i];
                if(planted[i]&&foot.grounded&&facing==m.facingRight) {
                    maxSlip=Math.max(maxSlip,Math.abs(world(m,i,lane,scale)-previous[i]));contacts++;
                }
                if(foot.grounded&&foot.lift!=0) throw new AssertionError("Floating planted foot");
                double jump=Math.hypot(foot.x-previousX[i],foot.lift-previousLift[i]);
                if(jump>maxJump) {maxJump=jump;jumpAt=String.format("t=%.3f leg=%s x=%.2f->%.2f lift=%.2f->%.2f",t,l.name,previousX[i],foot.x,previousLift[i],foot.lift);}
                double reach=Math.hypot(l.restFootX()+foot.x-m.pose.x(l.hx,l.shoulderY()),l.fy-foot.lift-m.pose.y(l.hx,l.shoulderY()));
                double length=Math.hypot(l.kx-l.hx,l.ky-l.hy)+Math.hypot(l.fx-l.kx,l.fy-l.ky);
                if(reach-length>maxReach) {maxReach=reach-length;reachAt=String.format("t=%.3f leg=%s x=%.2f",t,l.name,foot.x);}
                CatRig.skin(l,m.pose,foot.x,foot.lift);
                for(float v:l.vertices) if(!Float.isFinite(v)) throw new AssertionError("Invalid mesh");
                if(foot.grounded) {
                    int last=CatRig.ROWS*(CatRig.COLS+1)*2;
                    if(Math.abs(l.vertices[last+1]-CatRig.GROUND)>.0001) throw new AssertionError("Rendered contact leaves ground");
                }
            }
        }
        if(maxSlip>1e-7) throw new AssertionError("Contact slides "+maxSlip);
        if(maxReach>1) throw new AssertionError("Leg overextends: "+maxReach+" at "+reachAt);
        System.out.println("Peak recovery: "+jumpAt+"; peak reach: "+reachAt);
        if(maxJump*fps>260) throw new AssertionError("Foot teleport: "+maxJump);
        if(contacts<100) throw new AssertionError("No grounded samples");
        System.out.printf("PASS: %d fps lane %.0f, %d contacts, slip %.9f, max reach excess %.3f, max foot speed %.1f px/s%n",
            fps,lane,contacts,maxSlip,maxReach,maxJump*fps);
    }
    public static void main(String[] args) {
        for(int fps:new int[]{30,60,120}) for(double lane:new double[]{24,90,224}) checkRun(fps,lane);
        // C2 recovery: position, velocity and acceleration join a planted paw.
        for(double boundary:new double[]{0,CatRig.STANCE,1}) {
            double e=1e-5;
            for(boolean vertical:new boolean[]{false,true}) {
                double left=sample(boundary-e,vertical),at=sample(boundary,vertical),right=sample(boundary+e,vertical);
                if(Math.abs((at-left)/e-(right-at)/e)>.05) throw new AssertionError("Velocity kink");
                double al=(at-2*left+sample(boundary-2*e,vertical))/(e*e);
                double ar=(sample(boundary+2*e,vertical)-2*right+at)/(e*e);
                if(Math.abs(al-ar)>2) throw new AssertionError("Acceleration kink "+(al-ar));
            }
        }
        System.out.println("PASS: paw trajectories have continuous contact velocity and acceleration");
    }
    static double sample(double p,boolean vertical) {return vertical?CatRig.footLift(p):CatRig.footX(p);}
}
