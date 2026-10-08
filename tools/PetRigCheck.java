import com.example.shortsgesturecontrol.PetRig;
import com.example.shortsgesturecontrol.PetMotion;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Species assets, physical contacts, range/restarts and complete draw bounds. */
public final class PetRigCheck {
    static double world(PetMotion m,int i,double lane,double scale) {
        double x=m.rig.legs[i].restX+m.feet[i].x-256;
        return m.position*lane/scale+(m.facingRight?-x:x);
    }
    static double[] point(PetRig r,PetRig.Pose p,PetRig.Part part,double x,double y) {
        double a=p.angle(part),c=Math.cos(a),s=Math.sin(a);
        double tx=part.px+(x-part.px)*c-(y-part.py)*s,ty=part.py+(x-part.px)*s+(y-part.py)*c;
        if(part.headParent) {
            c=Math.cos(p.headAngle);s=Math.sin(p.headAngle);
            double xx=r.head.px+(tx-r.head.px)*c-(ty-r.head.py)*s;
            ty=r.head.py+(tx-r.head.px)*s+(ty-r.head.py)*c;tx=xx;
        }
        return new double[]{p.x(tx,ty),p.y(tx,ty)};
    }
    static void run(String kind,int fps,double lane) {
        PetRig r=new PetRig(kind);PetMotion m=new PetMotion(r);double scale=.6;
        double maxSlip=0,maxReach=0,maxSpeed=0,maxEnvelope=0,maxRest=0,still=0,peakLift=0;
        int contacts=0,turns=0,maxAir=0;String reachAt="",speedAt="",boundsAt="",airAt="";
        double[] priorWorld=new double[4],priorX=new double[4],priorLift=new double[4];boolean[] planted=new boolean[4];
        for(int frame=0;frame<fps*180;frame++) {
            boolean facing=m.facingRight;
            for(int i=0;i<4;i++){priorWorld[i]=world(m,i,lane,scale);priorX[i]=m.feet[i].x;priorLift[i]=m.feet[i].lift;planted[i]=m.feet[i].grounded;}
            double time=(double)frame/fps,oldCycle=m.cycle;
            boolean action=(time>8.15&&time<9.8)||(time>23.45&&time<25);
            double range=time>32&&time<33?0:lane;
            double dt=frame%997==0?.12:1.0/fps;
            m.advance(dt,range,scale,action);
            if(!Double.isFinite(m.position)||m.position<0||m.position>1||m.cycle<oldCycle)throw new AssertionError("Invalid roaming");
            if(facing!=m.facingRight)turns++;
            if(action||range==0||m.cycle>oldCycle+1e-8)still=0;else still+=dt;
            maxRest=Math.max(maxRest,still);
            int air=0;
            for(int i=0;i<4;i++) {
                PetRig.Leg l=r.legs[i];PetMotion.Foot f=m.feet[i];
                if(planted[i]&&f.grounded&&facing==m.facingRight) {maxSlip=Math.max(maxSlip,Math.abs(world(m,i,lane,scale)-priorWorld[i]));contacts++;}
                if(!f.grounded)air++;peakLift=Math.max(peakLift,f.lift);
                double speed=Math.hypot(f.x-priorX[i],f.lift-priorLift[i])/dt;
                if(speed>maxSpeed){maxSpeed=speed;speedAt="time="+time+" "+l.name;}
                double length=Math.hypot(l.restX+f.x-m.pose.x(l.hx,l.hy),l.fy-f.lift-m.pose.y(l.hx,l.hy));
                if(length-l.length>maxReach){maxReach=length-l.length;reachAt="time="+time+" "+l.name+" x="+f.x;}
                r.skin(l,m.pose,f.x,f.lift);
                for(float v:l.vertices)if(!Float.isFinite(v))throw new AssertionError("Invalid mesh");
                if(f.grounded) {
                    int last=PetRig.ROWS*(PetRig.COLS+1)*2;
                    if(Math.abs(l.vertices[last+1]-PetRig.GROUND)>.0001||f.lift!=0)throw new AssertionError("Floating planted paw");
                }
                for(int v=0;v<l.vertices.length;v+=2) {
                    double extent=Math.abs(l.vertices[v]-256);
                    if(extent>maxEnvelope){maxEnvelope=extent;boundsAt=l.name+" at "+time;}
                }
            }
            if(air>maxAir){maxAir=air;airAt="time="+time+" action="+action;}
            for(PetRig.Part part:r.parts)if(!(part instanceof PetRig.Leg)) {
                for(double x:new double[]{part.x,part.x+part.width})for(double y:new double[]{part.y,part.y+part.height}) {
                    double[] xy=point(r,m.pose,part,x,y);
                    if(xy[1]<0||xy[1]>512)throw new AssertionError("Vertical clipping "+part.name);
                    double extent=Math.abs(xy[0]-256);
                    if(extent>maxEnvelope){maxEnvelope=extent;boundsAt=part.name+" at "+time;}
                }
            }
        }
        if(maxSlip>1e-7||contacts<100)throw new AssertionError("Contact drift/no contacts: "+maxSlip);
        if(maxReach>1)throw new AssertionError(kind+" reach "+maxReach+" "+reachAt);
        if(maxSpeed>410)throw new AssertionError(kind+" recovery speed "+maxSpeed+" "+speedAt);
        if(maxEnvelope>256)throw new AssertionError(kind+" bounds "+maxEnvelope+" "+boundsAt);
        if(maxRest>2.8||turns<2)throw new AssertionError("Stuck roaming");
        if(peakLift<r.lift*.9)throw new AssertionError("No articulation");
        if(maxAir>3)throw new AssertionError("Walking loses all support contacts");
        System.out.printf("PASS %s %dfps lane %.0f: slip %.9f, reach %.3f, speed %.1f, bounds %.1f, rest %.2fs, %d turns, %d recovering paws%n",
            kind,fps,lane,maxSlip,maxReach,maxSpeed,maxEnvelope,maxRest,turns,maxAir);
        if(maxAir==4)System.out.println("All paws recovering: "+airAt);
    }
    public static void main(String[] args) throws Exception {
        for(String kind:new String[]{"dog","bunny","hamster","dragon"}) {
            PetRig r=new PetRig(kind);
            if(r.parts.length!=9)throw new AssertionError("Missing anatomy");
            if(!r.nearExtra.name.startsWith(kind.equals("dragon")?"wing":"ear"))throw new AssertionError("Wrong appendage");
            int totalFractional=0;
            for(PetRig.Part part:r.parts) {
                BufferedImage image=ImageIO.read(new File(args[0],kind+"_rig_"+part.name+".png"));
                if(image==null||image.getWidth()<20||image.getHeight()<20)throw new AssertionError("Missing/empty asset");
                int fractional=0,opaque=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int a=image.getRGB(x,y)>>>24;if(a>0&&a<255)fractional++;if(a>=240)opaque++;
                }
                totalFractional+=fractional;
                if(opaque<100)throw new AssertionError("Empty silhouette "+part.name);
            }
            if(totalFractional<100)throw new AssertionError("Species lost soft-edge alpha");
            for(int fps:new int[]{30,60,120})for(double lane:new double[]{24,90,224})run(kind,fps,lane);
        }
    }
}
