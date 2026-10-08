import com.example.shortsgesturecontrol.CatMotion;

/** Exercise repeated edge restarts, actions, frame stalls, and layout changes. */
public class CatMotionCheck {
    public static void main(String[] args) {
        for(double range:new double[]{24,48,90}) {
            for(int fps:new int[]{30,60,120}) {
                CatMotion motion=new CatMotion();
                double still=0,maxStill=0,previousCycle=0;
                int turns=0; boolean facing=motion.facingRight;
                for(int frame=0;frame<fps*180;frame++) {
                    double time=(double)frame/fps;
                    boolean action=time>45 && time<48;
                    double lane=time>80 && time<82?0:range;
                    double dt=frame%997==0?.12:1.0/fps;
                    motion.advance(dt,lane,.6,action);
                    if(!Float.isFinite(motion.position)||motion.position<0||motion.position>1)
                        throw new AssertionError("Out of scene bounds");
                    if(motion.cycle<previousCycle) throw new AssertionError("Gait ran backwards");
                    if(motion.facingRight!=facing) turns++;
                    facing=motion.facingRight;
                    if(action || lane==0) still=0;
                    else if(motion.cycle-previousCycle<1e-8) still+=dt;
                    else still=0;
                    maxStill=Math.max(maxStill,still);
                    if(still>2.8) throw new AssertionError("Cat stuck after edge restart at "+time+"s");
                    previousCycle=motion.cycle;
                }
                if(turns<10) throw new AssertionError("Too few repeated crossings: "+turns);
                System.out.printf("PASS: %dfps, lane %.0f, %d turns, max rest %.2fs%n",fps,range,turns,maxStill);
            }
        }
    }
}
