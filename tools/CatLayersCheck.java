import com.example.shortsgesturecontrol.CatLayers;
import com.example.shortsgesturecontrol.CatRig;
import com.example.shortsgesturecontrol.CatMotion;

/** Structural depth/attachment checks, not an assertion of visual approval. */
public class CatLayersCheck {
    public static void main(String[] args) {
        CatRig.Leg fore=CatRig.FRONT_NEAR;
        if(fore.hx<170||fore.hx>190) throw new AssertionError("Near foreleg shoulder is outside chest placement");
        if(fore.hx-fore.x!=68||fore.kx-fore.hx!=7||fore.fx-fore.hx!=-26
            ||fore.width!=120||fore.height!=153||fore.hy!=331||fore.ky!=396||fore.fy!=453||fore.offset!=0)
            throw new AssertionError("Foreleg relocation changed proportions or gait phase");
        for(CatRig.Leg l:new CatRig.Leg[]{CatRig.FRONT_NEAR,CatRig.FRONT_FAR}) {
            if(Math.abs(l.restFootX()-l.hx)>3) throw new AssertionError("Resting front paw is not below shoulder");
            if(l.shoulderRise<0||l.shoulderRise>6) throw new AssertionError("Front shoulder left chest attachment");
            CatRig.skin(l,new CatRig.Pose(),0,0);
            int bottom=CatRig.ROWS*(CatRig.COLS+1)*2;
            if(Math.abs(l.vertices[bottom]-(l.x+l.restFootShift))>.0001||l.vertices[bottom+1]!=CatRig.GROUND)
                throw new AssertionError("Neutral front paw placement changed");
        }
        double pawGap=CatRig.FRONT_FAR.restFootX()-fore.restFootX();
        if(Math.abs(pawGap-(CatRig.FRONT_FAR.hx-fore.hx))>3)
            throw new AssertionError("Front paws spread beyond shoulder spacing");
        for(CatRig.Leg l:new CatRig.Leg[]{CatRig.REAR_NEAR,CatRig.REAR_FAR}) {
            if(l.restFootShift!=0||l.shoulderRise!=0) throw new AssertionError("Rear resting pose changed");
            CatRig.skin(l,new CatRig.Pose(),0,0);
            for(int row=0;row<=CatRig.ROWS;row++) for(int col=0;col<=CatRig.COLS;col++) {
                int v=(row*(CatRig.COLS+1)+col)*2;
                if(Math.abs(l.vertices[v]-(l.x+l.width*col/CatRig.COLS))>.0001
                    ||Math.abs(l.vertices[v+1]-(l.y+l.height*row/CatRig.ROWS))>.0001)
                    throw new AssertionError("Rear standing mesh changed");
            }
        }
        if(CatLayers.FAR_LEGS.length!=2||CatLayers.NEAR_LEGS.length!=2) throw new AssertionError("Depth groups");
        boolean[] seen=new boolean[4];
        for(int i:CatLayers.FAR_LEGS) {
            if(!CatRig.LEGS[i].name.endsWith("_far")||seen[i]) throw new AssertionError("Foreground leg in rear group");
            seen[i]=true;
        }
        for(int i:CatLayers.NEAR_LEGS) {
            if(!CatRig.LEGS[i].name.endsWith("_near")||seen[i]) throw new AssertionError("Rear leg in foreground group");
            seen[i]=true;
        }
        for(boolean value:seen) if(!value) throw new AssertionError("Missing limb");
        for(CatRig.Leg l:CatRig.LEGS) {
            for(int x=0;x<=100;x++) {
                if(CatLayers.attachmentAlpha(l.name,x/100.0,1)!=255) throw new AssertionError("Paw alpha changed");
                if(l.name.endsWith("near")&&CatLayers.attachmentAlpha(l.name,x/100.0,0)!=0) throw new AssertionError("Closed joint cap remains");
            }
        }
        for(String part:new String[]{"tail","front_near","rear_near","head","body","front_far","rear_far"}) {
            for(int y=0;y<=100;y++) for(int x=0;x<=100;x++) {
                int a=CatLayers.attachmentAlpha(part,x/100.0,y/100.0);
                if(a<0||a>255) throw new AssertionError("Invalid alpha");
                if((part.equals("head")||part.equals("body")||part.endsWith("far"))&&a!=255) throw new AssertionError("Unrelated art altered");
                if(part.equals("tail")&&(y<=58||x>=50)&&a!=255) throw new AssertionError("Free tail contour faded");
            }
        }
        double maxRootError=0,maxEnvelope=0;
        CatMotion m=new CatMotion();
        for(int frame=0;frame<60*18;frame++) {
            m.advance(1.0/60,224,1,false);
            for(int i=0;i<4;i++) {
                CatRig.Leg l=CatRig.LEGS[i];CatRig.skin(l,m.pose,m.feet[i].x,m.feet[i].lift);
                for(int row=0;row<=CatRig.ROWS;row++) {
                    double y=l.y+l.height*row/CatRig.ROWS;
                    if(y>l.hy+3) break;
                    for(int col=0;col<=CatRig.COLS;col++) {
                        double x=l.x+l.width*col/CatRig.COLS;
                        int v=(row*(CatRig.COLS+1)+col)*2;
                        maxRootError=Math.max(maxRootError,Math.hypot(l.vertices[v]-m.pose.x(x,y-l.shoulderRise),l.vertices[v+1]-m.pose.y(x,y-l.shoulderRise)));
                    }
                }
                for(int v=0;v<l.vertices.length;v+=2) maxEnvelope=Math.max(maxEnvelope,Math.abs(l.vertices[v]-256));
            }
            // Conservative full-bitmap corners, including invisible padding.
            maxEnvelope=Math.max(maxEnvelope,partEnvelope(m.pose,CatLayers.TAIL_X,CatLayers.TAIL_Y,
                CatLayers.TAIL_WIDTH,CatLayers.TAIL_HEIGHT,CatLayers.TAIL_PIVOT_X,CatLayers.TAIL_PIVOT_Y,m.pose.tailAngle));
            maxEnvelope=Math.max(maxEnvelope,partEnvelope(m.pose,45,100,195,253,190,330,m.pose.headAngle));
            maxEnvelope=Math.max(maxEnvelope,partEnvelope(m.pose,170,230,235,139,190,330,0));
        }
        if(maxRootError>.0001) throw new AssertionError("Joint separates from body: "+maxRootError);
        if(maxEnvelope>256) throw new AssertionError("Rig escapes reserved horizontal envelope: "+maxEnvelope);
        System.out.printf("PASS: near/far groups; open root caps; untouched paws/free-tail/head/body; pinned joint error %.6f; envelope %.2f/256 px%n",maxRootError,maxEnvelope);
    }
    static double partEnvelope(CatRig.Pose pose,double x,double y,double width,double height,double px,double py,double angle) {
        double max=0,c=Math.cos(angle),s=Math.sin(angle);
        for(double xx:new double[]{x,x+width}) for(double yy:new double[]{y,y+height}) {
            double tx=px+(xx-px)*c-(yy-py)*s,ty=py+(xx-px)*s+(yy-py)*c;
            double worldY=pose.y(tx,ty);
            if(worldY<0||worldY>512) throw new AssertionError("Part escapes vertical envelope");
            max=Math.max(max,Math.abs(pose.x(tx,ty)-256));
        }
        return max;
    }
}
