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
                        maxRootError=Math.max(maxRootError,Math.hypot(l.vertices[v]-m.pose.x(x,y),l.vertices[v+1]-m.pose.y(x,y)));
                    }
                }
                for(int v=0;v<l.vertices.length;v+=2) maxEnvelope=Math.max(maxEnvelope,Math.abs(l.vertices[v]-256));
            }
            // Conservative full-bitmap corners, including invisible padding.
            for(double x:new double[]{CatLayers.TAIL_X,CatLayers.TAIL_X+CatLayers.TAIL_WIDTH})
                for(double y:new double[]{CatLayers.TAIL_Y,CatLayers.TAIL_Y+CatLayers.TAIL_HEIGHT}) {
                    double a=m.pose.tailAngle,c=Math.cos(a),s=Math.sin(a),px=CatLayers.TAIL_PIVOT_X,py=CatLayers.TAIL_PIVOT_Y;
                    double tx=px+(x-px)*c-(y-py)*s,ty=py+(x-px)*s+(y-py)*c;
                    maxEnvelope=Math.max(maxEnvelope,Math.abs(m.pose.x(tx,ty)-256));
                }
        }
        if(maxRootError>.0001) throw new AssertionError("Joint separates from body: "+maxRootError);
        if(maxEnvelope>256) throw new AssertionError("New tail escapes reserved horizontal envelope: "+maxEnvelope);
        System.out.printf("PASS: near/far groups; open root caps; untouched paws/free-tail/head/body; pinned joint error %.6f; envelope %.2f/256 px%n",maxRootError,maxEnvelope);
    }
}
