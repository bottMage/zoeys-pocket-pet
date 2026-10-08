import com.example.shortsgesturecontrol.CatRig;
import com.example.shortsgesturecontrol.CatMotion;
import com.example.shortsgesturecontrol.CatLayers;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders the same skinning vertices as Android; also checks ground contacts. */
public class CatRigPreview {
    static File assets;
    static java.util.Map<String,BufferedImage> cache=new java.util.HashMap<>();
    static BufferedImage load(String name) throws Exception {
        if(!cache.containsKey(name)) {
            BufferedImage bitmap=ImageIO.read(new File(assets,"cat_rig_"+name+".png"));
            for(int y=0;y<bitmap.getHeight();y++) for(int x=0;x<bitmap.getWidth();x++) {
                int p=bitmap.getRGB(x,y),mask=CatLayers.attachmentAlpha(name,(x+.5)/bitmap.getWidth(),(y+.5)/bitmap.getHeight());
                bitmap.setRGB(x,y,(p&0xffffff)|(((p>>>24)*mask/255)<<24));
            }
            cache.put(name,bitmap);
        }
        return cache.get(name);
    }
    static void part(Graphics2D g,String name,double x,double y,double w,double h,double px,double py,double angle) throws Exception {
        BufferedImage image=load(name);
        Graphics2D t=(Graphics2D)g.create();t.rotate(angle,px,py);
        AffineTransform transform=AffineTransform.getTranslateInstance(x,y);
        transform.scale(w/image.getWidth(),h/image.getHeight());
        t.drawImage(image,transform,null);t.dispose();
    }
    static void triangle(Graphics2D g,BufferedImage image,double[] sx,double[] sy,float[] v,int a,int b,int c) throws Exception {
        double x0=sx[a],y0=sy[a],x1=sx[b],y1=sy[b],x2=sx[c],y2=sy[c];
        AffineTransform source=new AffineTransform(x1-x0,y1-y0,x2-x0,y2-y0,x0,y0);
        AffineTransform target=new AffineTransform(v[b*2]-v[a*2],v[b*2+1]-v[a*2+1],
            v[c*2]-v[a*2],v[c*2+1]-v[a*2+1],v[a*2],v[a*2+1]);
        target.concatenate(source.createInverse());
        Path2D clip=new Path2D.Double();
        clip.moveTo(v[a*2],v[a*2+1]); clip.lineTo(v[b*2],v[b*2+1]);
        clip.lineTo(v[c*2],v[c*2+1]);clip.closePath();
        Graphics2D t=(Graphics2D)g.create();
        t.clip(clip); t.drawImage(image,target,null);t.dispose();
    }
    static void leg(Graphics2D g,CatRig.Leg leg,CatRig.Pose pose,double footX,double lift) throws Exception {
        CatRig.skin(leg,pose,footX,lift);
        BufferedImage image=load(leg.name);
        double[] sx=new double[(CatRig.COLS+1)*(CatRig.ROWS+1)],sy=new double[sx.length];
        for(int r=0;r<=CatRig.ROWS;r++) for(int c=0;c<=CatRig.COLS;c++) {
            int i=r*(CatRig.COLS+1)+c;
            sx[i]=(double)image.getWidth()*c/CatRig.COLS;
            sy[i]=(double)image.getHeight()*r/CatRig.ROWS;
        }
        for(int r=0;r<CatRig.ROWS;r++) for(int c=0;c<CatRig.COLS;c++) {
            int a=r*(CatRig.COLS+1)+c,b=a+1,d=a+CatRig.COLS+1,e=d+1;
            triangle(g,image,sx,sy,leg.vertices,a,b,d);
            triangle(g,image,sx,sy,leg.vertices,b,e,d);
        }
    }
    static void verify() {
        double maxSlip=0;
        for(CatRig.Leg l:CatRig.LEGS) {
            double world=l.restFootX()+CatRig.footX(.1);
            for(int i=1;i<=500;i++) {
                double p=.1+i*.001;
                maxSlip=Math.max(maxSlip,Math.abs(l.restFootX()+CatRig.footX(p)-(p-.1)*CatRig.STRIDE-world));
            }
            double meshAnchor=Double.NaN;
            for(int i=0;i<=500;i++) {
                double p=.1+i*.001;
                CatRig.skin(l,p-l.offset,1,CatRig.bodyY(p-l.offset,1));
                int index=CatRig.ROWS*(CatRig.COLS+1)*2;
                double footWorld=l.vertices[index]-(p-.1)*CatRig.STRIDE;
                if(i==0) meshAnchor=footWorld;
                if(Math.abs(footWorld-meshAnchor)>.0001) throw new AssertionError("Rendered paw slips");
                double expectedBottom=l.y+l.height;
                if(Math.abs(l.vertices[index+1]-expectedBottom)>.0001) throw new AssertionError("Paw not on ground");
            }
            for(int i=0;i<1000;i++) {
                double p=i/1000.0;
                CatRig.skin(l,p,1,CatRig.bodyY(p,1));
                for(float v:l.vertices) if(!Float.isFinite(v)) throw new AssertionError("Non-finite vertex");
            }
        }
        if(maxSlip>1e-6) throw new AssertionError("Paw slips: "+maxSlip);
        for(double boundary:new double[]{0,CatRig.STANCE,1}) {
            double eps=1e-6;
            if(Math.abs(CatRig.footX(boundary-eps)-CatRig.footX(boundary+eps))>.001)
                throw new AssertionError("Discontinuous contact");
            double a=(CatRig.footX(boundary)-CatRig.footX(boundary-eps))/eps;
            double b=(CatRig.footX(boundary+eps)-CatRig.footX(boundary))/eps;
            if(Math.abs(a-b)>.01) throw new AssertionError("Discontinuous velocity");
        }
        System.out.println("PASS: stance world-space slip="+maxSlip+"px; finite skinning; continuous touchdown velocity");
    }
    public static void main(String[] args) throws Exception {
        assets=new File(args[0]); File out=new File(args[1]);out.mkdirs(); verify();
        int frameCount=args.length>2?Integer.parseInt(args[2]):180;
        boolean roaming=args.length>3&&args[3].equals("roam");
        CatMotion motion=new CatMotion();CatRig.Pose pose=new CatRig.Pose();
        for(int frame=0;frame<frameCount;frame++) {
            double cycle=frame/60.0*CatMotion.WALK_SPEED/CatRig.STRIDE,blend=1;
            if(roaming) {
                motion.advance(1.0/60,224,1,false);
                cycle=motion.cycle;blend=motion.blend;
            }
            if(roaming) pose=motion.pose;
            else pose.sample(cycle,blend,frame/60.0);
            int sceneWidth=roaming?800:640;
            BufferedImage image=new BufferedImage(sceneWidth,540,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(233,234,247));g.fillRect(0,0,sceneWidth,540);
            g.setColor(new Color(185,224,194));g.fillRect(0,488,sceneWidth,52);
            double rootX=roaming?32+motion.position*224:64;
            g.setColor(new Color(147,184,154));g.fill(new Ellipse2D.Double(rootX+108,480,240,16));
            // Move the scene under the rig to make world-space contacts visible.
            g.setColor(new Color(150,193,161));
            for(int x=-100;x<800;x+=40) {
                int mark=roaming?x:x+(int)(cycle*CatRig.STRIDE)%40;
                g.fillRect(mark,490,2,9);
            }
            g.translate(rootX,20);
            if(roaming&&motion.facingRight) {g.translate(512,0);g.scale(-1,1);}
            Graphics2D trunk=(Graphics2D)g.create();
            trunk.translate(0,pose.y);trunk.rotate(pose.angle,285,333);
            for(int i:CatLayers.FAR_LEGS) {
                CatRig.Leg l=CatRig.LEGS[i];
                double x=roaming?motion.feet[i].x:CatRig.footX(cycle+l.offset);
                double lift=roaming?motion.feet[i].lift:CatRig.footLift(cycle+l.offset);
                leg(g,l,pose,x,lift);
            }
            part(trunk,"body",170,230,235,139,190,330,0);
            part(trunk,"tail",CatLayers.TAIL_X,CatLayers.TAIL_Y,CatLayers.TAIL_WIDTH,CatLayers.TAIL_HEIGHT,
                CatLayers.TAIL_PIVOT_X,CatLayers.TAIL_PIVOT_Y,pose.tailAngle);
            for(int i:CatLayers.NEAR_LEGS) {
                CatRig.Leg l=CatRig.LEGS[i];
                double x=roaming?motion.feet[i].x:CatRig.footX(cycle+l.offset);
                double lift=roaming?motion.feet[i].lift:CatRig.footLift(cycle+l.offset);
                leg(g,l,pose,x,lift);
            }
            part(trunk,"head",45,100,195,253,190,330,pose.headAngle);
            trunk.dispose();
            g.dispose();ImageIO.write(image,"png",new File(out,String.format("frame-%03d.png",frame)));
        }
    }
}
