import com.example.shortsgesturecontrol.CatMotion;
import com.example.shortsgesturecontrol.CatRig;

/** Measure the requested motion ranges, independently of subjective approval. */
public final class CatLivelinessCheck {
    public static void main(String[] args) {
        CatMotion m=new CatMotion();
        double peakSpeed=0,peakLift=0,peakBody=0;
        double headMin=Double.POSITIVE_INFINITY,headMax=Double.NEGATIVE_INFINITY;
        double tailMin=Double.POSITIVE_INFINITY,tailMax=Double.NEGATIVE_INFINITY;
        double[] bendMin={10,10,10,10},bendMax={0,0,0,0};
        int maxAirborne=0;
        for(int frame=0;frame<120*30;frame++) {
            double before=m.position;
            m.advance(1.0/120,224,1,false);
            peakSpeed=Math.max(peakSpeed,Math.abs(m.position-before)*224*120);
            peakBody=Math.max(peakBody,m.pose.y);
            headMin=Math.min(headMin,m.pose.angle+m.pose.headAngle);
            headMax=Math.max(headMax,m.pose.angle+m.pose.headAngle);
            tailMin=Math.min(tailMin,m.pose.angle+m.pose.tailAngle);
            tailMax=Math.max(tailMax,m.pose.angle+m.pose.tailAngle);
            int airborne=0;
            for(int i=0;i<4;i++) {
                CatRig.Leg l=CatRig.LEGS[i];CatMotion.Foot f=m.feet[i];
                peakLift=Math.max(peakLift,f.lift);
                if(!f.grounded) airborne++;
                double upper=Math.hypot(l.kx-l.hx,l.ky-l.hy);
                double lower=Math.hypot(l.fx-l.kx,l.fy-l.ky);
                double distance=Math.hypot(l.restFootX()+f.x-m.pose.x(l.hx,l.shoulderY()),
                    l.fy-f.lift-m.pose.y(l.hx,l.shoulderY()));
                double cosine=(upper*upper+lower*lower-distance*distance)/(2*upper*lower);
                double bend=Math.acos(Math.max(-1,Math.min(1,cosine)));
                bendMin[i]=Math.min(bendMin[i],bend);bendMax[i]=Math.max(bendMax[i],bend);
            }
            maxAirborne=Math.max(maxAirborne,airborne);
        }
        if(peakSpeed<70||peakSpeed>CatMotion.WALK_SPEED+.001) throw new AssertionError("Travel is not brisk/bounded");
        if(peakLift<25||peakLift>CatRig.PAW_LIFT+.001) throw new AssertionError("Paw lift is not broader/bounded");
        if(peakBody<7||peakBody>8.401) throw new AssertionError("Weight transfer is not broader/bounded");
        if(headMax-headMin<.07||tailMax-tailMin<.15) throw new AssertionError("Head/tail remain too still");
        for(int i=0;i<4;i++) {
            double range=bendMax[i]-bendMin[i];
            if(range<.45) throw new AssertionError("Insufficient joint articulation: "+CatRig.LEGS[i].name);
            System.out.printf("%s joint angle range %.1f degrees%n",CatRig.LEGS[i].name,Math.toDegrees(range));
        }
        if(maxAirborne==4) throw new AssertionError("Walking loses every support contact");
        System.out.printf("PASS: %.1f px/s travel, %.1f px lift, %.1f px crouch; head %.1f / tail %.1f degree arcs; max %d recovering paws%n",
            peakSpeed,peakLift,peakBody,Math.toDegrees(headMax-headMin),Math.toDegrees(tailMax-tailMin),maxAirborne);
    }
}
